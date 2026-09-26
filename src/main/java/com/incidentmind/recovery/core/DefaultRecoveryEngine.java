package com.incidentmind.recovery.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.recovery.model.RecoveryResult;
import com.incidentmind.recovery.policy.BackoffStrategy;
import com.incidentmind.recovery.policy.RecoveryPolicy;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.tool.core.PayloadSanitizer;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DefaultRecoveryEngine implements RecoveryEngine {

    private final RecoveryPolicy recoveryPolicy;
    private final RecoveryAttemptRepository recoveryAttemptRepository;
    private final AuditService auditService;
    private final ToolGateway toolGateway;
    private final BackoffStrategy backoffStrategy;

    public DefaultRecoveryEngine(RecoveryPolicy recoveryPolicy,
                                 RecoveryAttemptRepository recoveryAttemptRepository,
                                 AuditService auditService,
                                 ToolGateway toolGateway,
                                 BackoffStrategy backoffStrategy) {
        this.recoveryPolicy = recoveryPolicy;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
        this.auditService = auditService;
        this.toolGateway = toolGateway;
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public RecoveryDecision decide(RecoveryContext context) {
        if (context == null) {
            return RecoveryDecision.abort("Null recovery context", Map.of());
        }

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        // Record RECOVERY_DECISION audit event
        Map<String, Object> decisionAuditData = new HashMap<>();
        decisionAuditData.put("recoveryType", decision.getRecoveryType().name());
        decisionAuditData.put("shouldRecover", decision.isShouldRecover());
        decisionAuditData.put("reason", decision.getReason());
        decisionAuditData.put("attemptNumber", context.getAttemptNumber());
        decisionAuditData.put("maxAttempts", context.getMaxAttempts());
        if (decision.getTargetToolName() != null) {
            decisionAuditData.put("targetToolName", decision.getTargetToolName());
        }
        if (decision.getBackoffMs() > 0) {
            decisionAuditData.put("backoffMs", decision.getBackoffMs());
        }

        auditService.recordEvent(
                context.getInvestigationId(),
                context.getTaskId(),
                context.getAgentRunId(),
                AuditEventType.RECOVERY_DECISION,
                ActorType.SYSTEM,
                "recovery-engine",
                decisionAuditData,
                context.getCorrelationId()
        );

        log.info("Recovery decision made: type={}, shouldRecover={}, attempt={}/{}, reason='{}'",
                decision.getRecoveryType(), decision.isShouldRecover(),
                context.getAttemptNumber(), context.getMaxAttempts(), decision.getReason());

        return decision;
    }

    @Override
    @Transactional
    public RecoveryResult recover(RecoveryContext context, RecoveryDecision decision) {
        if (context == null || decision == null) {
            return RecoveryResult.failure(RecoveryType.ABORT, "Context or decision is null", null, null, 0L);
        }

        Instant startTime = Instant.now();
        long startMs = System.currentTimeMillis();

        String reasonStr = decision.getReason();
        if (reasonStr != null && reasonStr.length() > 250) {
            reasonStr = reasonStr.substring(0, 250);
        } else if (reasonStr == null) {
            reasonStr = "Automated recovery execution";
        }

        Map<String, Object> detailsMap = new HashMap<>(decision.getDecisionData());
        detailsMap.put("toolName", context.getToolName());
        detailsMap.put("taskType", context.getTaskType());
        if (context.getErrorCode() != null) detailsMap.put("errorCode", context.getErrorCode());
        if (context.getHttpStatus() != null) detailsMap.put("httpStatus", context.getHttpStatus());

        // 1. Persist initial RecoveryAttempt in STARTED status
        RecoveryAttempt attempt = RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(context.getInvestigationId())
                .taskId(context.getTaskId())
                .toolCallId(context.getToolCallId())
                .recoveryType(decision.getRecoveryType())
                .reason(reasonStr)
                .attemptNumber(context.getAttemptNumber())
                .status(RecoveryStatus.STARTED)
                .details(PayloadSanitizer.sanitize(detailsMap))
                .createdAt(startTime)
                .build();

        attempt = recoveryAttemptRepository.save(attempt);

        // 2. Record RECOVERY_STARTED audit event
        Map<String, Object> startedAuditData = new HashMap<>();
        startedAuditData.put("recoveryAttemptId", attempt.getId().toString());
        startedAuditData.put("recoveryType", decision.getRecoveryType().name());
        startedAuditData.put("attemptNumber", context.getAttemptNumber());
        startedAuditData.put("reason", reasonStr);

        auditService.recordEvent(
                context.getInvestigationId(),
                context.getTaskId(),
                context.getAgentRunId(),
                AuditEventType.RECOVERY_STARTED,
                ActorType.SYSTEM,
                "recovery-engine",
                startedAuditData,
                context.getCorrelationId()
        );

        // 3. Execute recovery based on decision type
        RecoveryResult result;
        try {
            if (decision.getRecoveryType() == RecoveryType.RETRY || decision.getRecoveryType() == RecoveryType.BACKOFF) {
                // Apply backoff delay
                if (decision.getBackoffMs() > 0) {
                    backoffStrategy.applyBackoff(decision.getBackoffMs());
                }

                if (context.getToolRequest() != null) {
                    // Retry tool execution via ToolGateway
                    ToolResult toolResult = toolGateway.invokeTool(context.getToolRequest());
                    long durationMs = System.currentTimeMillis() - startMs;

                    if (toolResult.isSuccess()) {
                        result = RecoveryResult.success(decision.getRecoveryType(), toolResult, attempt.getId(), durationMs);
                    } else {
                        result = RecoveryResult.failure(decision.getRecoveryType(), "Tool retry failed", toolResult, attempt.getId(), durationMs);
                    }
                } else if (context.getAgentExecutionContext() != null) {
                    // Agent-level retry
                    long durationMs = System.currentTimeMillis() - startMs;
                    result = RecoveryResult.successAgent(decision.getRecoveryType(), null, attempt.getId(), durationMs);
                } else {
                    long durationMs = System.currentTimeMillis() - startMs;
                    result = RecoveryResult.failure(decision.getRecoveryType(), "No executable request in context", null, attempt.getId(), durationMs);
                }
            } else if (decision.getRecoveryType() == RecoveryType.FALLBACK_TOOL) {
                // Execute fallback tool
                if (decision.getTargetToolName() != null && context.getToolRequest() != null) {
                    ToolRequest fallbackRequest = ToolRequest.builder()
                            .toolName(decision.getTargetToolName())
                            .agentRunId(context.getToolRequest().getAgentRunId())
                            .taskId(context.getToolRequest().getTaskId())
                            .investigationId(context.getToolRequest().getInvestigationId())
                            .parameters(context.getToolRequest().getParameters())
                            .build();

                    ToolResult fallbackResult = toolGateway.invokeTool(fallbackRequest);
                    long durationMs = System.currentTimeMillis() - startMs;

                    if (fallbackResult.isSuccess()) {
                        result = RecoveryResult.success(RecoveryType.FALLBACK_TOOL, fallbackResult, attempt.getId(), durationMs);
                    } else {
                        result = RecoveryResult.failure(RecoveryType.FALLBACK_TOOL, "Fallback tool execution failed", fallbackResult, attempt.getId(), durationMs);
                    }
                } else {
                    long durationMs = System.currentTimeMillis() - startMs;
                    result = RecoveryResult.failure(RecoveryType.FALLBACK_TOOL, "Target fallback tool not specified", null, attempt.getId(), durationMs);
                }
            } else if (decision.getRecoveryType() == RecoveryType.REPLAN) {
                long durationMs = System.currentTimeMillis() - startMs;
                result = RecoveryResult.failure(RecoveryType.REPLAN, "Retries exhausted; replanning required", context.getToolResult(), attempt.getId(), durationMs);
            } else {
                long durationMs = System.currentTimeMillis() - startMs;
                result = RecoveryResult.failure(decision.getRecoveryType(), decision.getReason(), context.getToolResult(), attempt.getId(), durationMs);
            }
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Exception during recovery execution", ex);
            result = RecoveryResult.failure(decision.getRecoveryType(), "Recovery execution exception: " + ex.getMessage(), context.getToolResult(), attempt.getId(), durationMs);
        }

        // 4. Update RecoveryAttempt record
        Instant completedTime = Instant.now();
        attempt.setStatus(result.isSuccess() ? RecoveryStatus.SUCCESS : RecoveryStatus.FAILED);
        attempt.setCompletedAt(completedTime);
        if (result.getRecoveredToolResult() != null && result.getRecoveredToolResult().getToolCallId() != null) {
            attempt.setToolCallId(result.getRecoveredToolResult().getToolCallId());
        }
        recoveryAttemptRepository.save(attempt);

        // 5. Emit final recovery audit events
        AuditEventType eventType;
        if (result.isSuccess()) {
            eventType = AuditEventType.RECOVERY_SUCCEEDED;
        } else if (decision.getRecoveryType() == RecoveryType.REPLAN) {
            eventType = AuditEventType.RECOVERY_EXHAUSTED;
        } else {
            eventType = AuditEventType.RECOVERY_FAILED;
        }

        Map<String, Object> finishAuditData = new HashMap<>();
        finishAuditData.put("recoveryAttemptId", attempt.getId().toString());
        finishAuditData.put("recoveryType", decision.getRecoveryType().name());
        finishAuditData.put("status", attempt.getStatus().name());
        finishAuditData.put("durationMs", result.getDurationMs());
        if (result.getMessage() != null) finishAuditData.put("message", result.getMessage());

        auditService.recordEvent(
                context.getInvestigationId(),
                context.getTaskId(),
                context.getAgentRunId(),
                eventType,
                ActorType.SYSTEM,
                "recovery-engine",
                finishAuditData,
                context.getCorrelationId()
        );

        // Also record generic RECOVERY_COMPLETED for consistency
        auditService.recordEvent(
                context.getInvestigationId(),
                context.getTaskId(),
                context.getAgentRunId(),
                AuditEventType.RECOVERY_COMPLETED,
                ActorType.SYSTEM,
                "recovery-engine",
                finishAuditData,
                context.getCorrelationId()
        );

        log.info("Recovery finished: type={}, status={}, duration={}ms",
                decision.getRecoveryType(), attempt.getStatus(), result.getDurationMs());

        return result;
    }
}
