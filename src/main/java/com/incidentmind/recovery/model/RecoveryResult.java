package com.incidentmind.recovery.model;

import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.tool.model.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResult {

    private boolean success;
    private RecoveryStatus status;
    private RecoveryType recoveryType;
    private ToolResult recoveredToolResult;
    private AgentExecutionResult recoveredAgentResult;
    private String message;
    private Long durationMs;
    private UUID recoveryAttemptId;

    public static RecoveryResult success(RecoveryType recoveryType,
                                         ToolResult recoveredToolResult,
                                         UUID recoveryAttemptId,
                                         Long durationMs) {
        return RecoveryResult.builder()
                .success(true)
                .status(RecoveryStatus.SUCCESS)
                .recoveryType(recoveryType)
                .recoveredToolResult(recoveredToolResult)
                .recoveryAttemptId(recoveryAttemptId)
                .durationMs(durationMs)
                .message("Recovery operation completed successfully")
                .build();
    }

    public static RecoveryResult successAgent(RecoveryType recoveryType,
                                              AgentExecutionResult recoveredAgentResult,
                                              UUID recoveryAttemptId,
                                              Long durationMs) {
        return RecoveryResult.builder()
                .success(true)
                .status(RecoveryStatus.SUCCESS)
                .recoveryType(recoveryType)
                .recoveredAgentResult(recoveredAgentResult)
                .recoveryAttemptId(recoveryAttemptId)
                .durationMs(durationMs)
                .message("Agent recovery operation completed successfully")
                .build();
    }

    public static RecoveryResult failure(RecoveryType recoveryType,
                                         String message,
                                         ToolResult failedToolResult,
                                         UUID recoveryAttemptId,
                                         Long durationMs) {
        return RecoveryResult.builder()
                .success(false)
                .status(RecoveryStatus.FAILED)
                .recoveryType(recoveryType)
                .recoveredToolResult(failedToolResult)
                .recoveryAttemptId(recoveryAttemptId)
                .durationMs(durationMs)
                .message(message)
                .build();
    }
}
