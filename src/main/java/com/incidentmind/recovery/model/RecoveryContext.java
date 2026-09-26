package com.incidentmind.recovery.model;

import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryContext {

    private UUID investigationId;
    private UUID taskId;
    private UUID agentRunId;
    private UUID toolCallId;
    private UUID correlationId;

    private String taskType;
    private String agentName;
    private String toolName;
    private String fallbackToolName;

    private ErrorClassification errorClassification;
    private Integer httpStatus;
    private String errorCode;
    private String errorMessage;

    @Builder.Default
    private int attemptNumber = 1;

    @Builder.Default
    private int maxAttempts = 3;

    @Builder.Default
    private List<RecoveryAttempt> previousAttempts = List.of();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    private ToolRequest toolRequest;
    private ToolResult toolResult;
    private AgentExecutionContext agentExecutionContext;
    private AgentExecutionResult agentExecutionResult;

    public static RecoveryContext forToolFailure(ToolResult toolResult,
                                                 ToolRequest toolRequest,
                                                 AgentExecutionContext executionContext,
                                                 int attemptNumber,
                                                 int maxAttempts,
                                                 List<RecoveryAttempt> previousAttempts) {
        UUID invId = executionContext != null && executionContext.getInvestigation() != null
                ? executionContext.getInvestigation().getId()
                : (toolRequest != null ? toolRequest.getInvestigationId() : null);

        UUID taskId = executionContext != null && executionContext.getTask() != null
                ? executionContext.getTask().getId()
                : (toolRequest != null ? toolRequest.getTaskId() : null);

        UUID agentRunId = toolRequest != null ? toolRequest.getAgentRunId() : null;
        UUID correlationId = executionContext != null ? executionContext.getCorrelationId() : null;

        String taskType = executionContext != null && executionContext.getTask() != null
                ? executionContext.getTask().getTaskType()
                : null;

        String toolName = toolResult != null ? toolResult.getToolName() : (toolRequest != null ? toolRequest.getToolName() : "unknown-tool");

        return RecoveryContext.builder()
                .investigationId(invId)
                .taskId(taskId)
                .agentRunId(agentRunId)
                .toolCallId(toolResult != null ? toolResult.getToolCallId() : null)
                .correlationId(correlationId)
                .taskType(taskType)
                .toolName(toolName)
                .errorClassification(toolResult != null ? toolResult.getErrorClassification() : ErrorClassification.UNKNOWN)
                .httpStatus(toolResult != null ? toolResult.getHttpStatus() : null)
                .errorCode(toolResult != null ? toolResult.getErrorCode() : null)
                .errorMessage(toolResult != null ? toolResult.getErrorMessage() : null)
                .attemptNumber(attemptNumber)
                .maxAttempts(maxAttempts > 0 ? maxAttempts : 3)
                .previousAttempts(previousAttempts != null ? previousAttempts : List.of())
                .toolRequest(toolRequest)
                .toolResult(toolResult)
                .agentExecutionContext(executionContext)
                .build();
    }

    public static RecoveryContext forAgentFailure(AgentExecutionResult agentResult,
                                                  AgentExecutionContext executionContext,
                                                  int attemptNumber,
                                                  int maxAttempts,
                                                  List<RecoveryAttempt> previousAttempts) {
        UUID invId = executionContext != null && executionContext.getInvestigation() != null
                ? executionContext.getInvestigation().getId()
                : null;

        UUID taskId = executionContext != null && executionContext.getTask() != null
                ? executionContext.getTask().getId()
                : null;

        UUID correlationId = executionContext != null ? executionContext.getCorrelationId() : null;

        String taskType = executionContext != null && executionContext.getTask() != null
                ? executionContext.getTask().getTaskType()
                : null;

        return RecoveryContext.builder()
                .investigationId(invId)
                .taskId(taskId)
                .correlationId(correlationId)
                .taskType(taskType)
                .errorClassification(ErrorClassification.UNKNOWN)
                .errorCode(agentResult != null ? agentResult.getErrorCode() : "AGENT_FAILURE")
                .errorMessage(agentResult != null ? agentResult.getErrorMessage() : "Agent execution failed")
                .attemptNumber(attemptNumber)
                .maxAttempts(maxAttempts > 0 ? maxAttempts : 3)
                .previousAttempts(previousAttempts != null ? previousAttempts : List.of())
                .agentExecutionContext(executionContext)
                .agentExecutionResult(agentResult)
                .build();
    }
}
