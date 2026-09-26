package com.incidentmind.tool.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.filter.CorrelationContext;
import com.incidentmind.tool.entity.ToolCall;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.failure.FailureInjector;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolExecutionContext;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import com.incidentmind.tool.repository.ToolCallRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class DefaultToolGateway implements ToolGateway {

    private final ToolRegistry toolRegistry;
    private final ToolCallRepository toolCallRepository;
    private final AuditService auditService;
    private final FailureInjector failureInjector;

    public DefaultToolGateway(ToolRegistry toolRegistry,
                              ToolCallRepository toolCallRepository,
                              AuditService auditService,
                              FailureInjector failureInjector) {
        this.toolRegistry = toolRegistry;
        this.toolCallRepository = toolCallRepository;
        this.auditService = auditService;
        this.failureInjector = failureInjector;
    }

    @Override
    @Transactional
    public ToolResult invokeTool(ToolRequest request) {
        if (request == null || request.getToolName() == null || request.getToolName().isBlank()) {
            throw new IllegalArgumentException("Tool request and toolName must not be empty");
        }

        UUID correlationId = CorrelationContext.getCorrelationIdAsUuid();
        Tool tool = toolRegistry.getTool(request.getToolName());

        Map<String, Object> sanitizedRequestPayload = PayloadSanitizer.sanitize(request.getParameters());
        Instant startTime = Instant.now();
        long startEpochMs = System.currentTimeMillis();

        // 1. Persist initial ToolCall record
        ToolCall toolCall = ToolCall.builder()
                .id(UUID.randomUUID())
                .agentRunId(request.getAgentRunId())
                .toolName(tool.getName())
                .toolType(tool.getType())
                .requestPayload(sanitizedRequestPayload)
                .status(ToolCallStatus.STARTED)
                .attemptNumber(1)
                .startedAt(startTime)
                .createdAt(startTime)
                .build();

        toolCall = toolCallRepository.save(toolCall);
        log.info("Tool invocation started: tool='{}', type='{}', toolCallId='{}', correlationId='{}'",
                tool.getName(), tool.getType(), toolCall.getId(), correlationId);

        // 2. Record TOOL_CALL_STARTED audit event
        Map<String, Object> startAuditData = new HashMap<>();
        startAuditData.put("toolCallId", toolCall.getId().toString());
        startAuditData.put("toolName", tool.getName());
        startAuditData.put("toolType", tool.getType());
        startAuditData.put("requestPayload", sanitizedRequestPayload);

        auditService.recordEvent(
                request.getInvestigationId(),
                request.getTaskId(),
                request.getAgentRunId(),
                AuditEventType.TOOL_CALL_STARTED,
                ActorType.TOOL,
                tool.getName(),
                startAuditData,
                correlationId
        );

        // 3. Execute with potential Failure Injection or real Tool execution
        ToolExecutionContext executionContext = ToolExecutionContext.builder()
                .correlationId(correlationId)
                .agentRunId(request.getAgentRunId())
                .investigationId(request.getInvestigationId())
                .taskId(request.getTaskId())
                .build();

        ToolResult toolResult;
        Optional<ToolResult> injectedFailure = failureInjector.maybeInjectFailure(request, tool.getType());
        if (injectedFailure.isPresent()) {
            toolResult = injectedFailure.get();
        } else {
            try {
                toolResult = tool.execute(request, executionContext);
            } catch (Exception ex) {
                long durationMs = System.currentTimeMillis() - startEpochMs;
                log.error("Unhandled exception during tool execution for {}", tool.getName(), ex);
                toolResult = ToolResult.failure(
                        tool.getName(),
                        tool.getType(),
                        ToolCallStatus.FAILED,
                        null,
                        "UNHANDLED_EXCEPTION",
                        ex.getMessage(),
                        ErrorClassification.UNKNOWN,
                        Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()),
                        Map.of(),
                        durationMs
                );
            }
        }

        // Calculate and enforce execution metrics
        long totalDurationMs = System.currentTimeMillis() - startEpochMs;
        if (toolResult.getDurationMs() == null || toolResult.getDurationMs() <= 0) {
            toolResult.setDurationMs(totalDurationMs);
        }

        Instant completedTime = Instant.now();
        Map<String, Object> sanitizedResponsePayload = PayloadSanitizer.sanitize(toolResult.getResponsePayload());

        // 4. Update ToolCall record
        toolCall.setStatus(toolResult.getStatus());
        toolCall.setHttpStatus(toolResult.getHttpStatus());
        toolCall.setResponsePayload(sanitizedResponsePayload);
        toolCall.setErrorCode(toolResult.getErrorCode());
        toolCall.setErrorMessage(toolResult.getErrorMessage());
        toolCall.setCompletedAt(completedTime);
        toolCall.setDurationMs(toolResult.getDurationMs());
        toolCallRepository.save(toolCall);

        // 5. Record TOOL_CALL_COMPLETED or TOOL_CALL_FAILED audit event
        Map<String, Object> completionAuditData = new HashMap<>();
        completionAuditData.put("toolCallId", toolCall.getId().toString());
        completionAuditData.put("toolName", tool.getName());
        completionAuditData.put("toolType", tool.getType());
        completionAuditData.put("status", toolResult.getStatus().name());
        completionAuditData.put("httpStatus", toolResult.getHttpStatus());
        completionAuditData.put("durationMs", toolResult.getDurationMs());
        if (toolResult.getRateLimitInfo() != null && !toolResult.getRateLimitInfo().isEmpty()) {
            completionAuditData.put("rateLimitInfo", toolResult.getRateLimitInfo());
        }
        if (!toolResult.isSuccess()) {
            completionAuditData.put("errorCode", toolResult.getErrorCode());
            completionAuditData.put("errorMessage", toolResult.getErrorMessage());
            completionAuditData.put("errorClassification", toolResult.getErrorClassification() != null ? toolResult.getErrorClassification().name() : null);
        }

        AuditEventType completionEventType = toolResult.isSuccess()
                ? AuditEventType.TOOL_CALL_COMPLETED
                : AuditEventType.TOOL_CALL_FAILED;

        auditService.recordEvent(
                request.getInvestigationId(),
                request.getTaskId(),
                request.getAgentRunId(),
                completionEventType,
                ActorType.TOOL,
                tool.getName(),
                completionAuditData,
                correlationId
        );

        // 6. Enrich and return ToolResult
        toolResult.setToolCallId(toolCall.getId());
        toolResult.setCorrelationId(correlationId);
        toolResult.setRequestPayload(sanitizedRequestPayload);

        log.info("Tool invocation finished: tool='{}', status='{}', httpStatus='{}', durationMs={}ms",
                tool.getName(), toolResult.getStatus(), toolResult.getHttpStatus(), toolResult.getDurationMs());

        return toolResult;
    }
}
