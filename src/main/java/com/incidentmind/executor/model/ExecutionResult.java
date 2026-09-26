package com.incidentmind.executor.model;

import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.task.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    private boolean success;
    private TaskStatus finalTaskStatus;
    private AgentExecutionResult agentResult;
    private CriticResult criticResult;
    private String message;
    private Long durationMs;
    public boolean isAccepted() {
        return success && criticResult != null && criticResult.isAccepted();
    }

    public boolean isRejected() {
        return criticResult != null && criticResult.isRejected();
    }

    public boolean isBlocked() {
        return finalTaskStatus == TaskStatus.BLOCKED;
    }

    public static ExecutionResult accepted(TaskStatus status,
                                           AgentExecutionResult agentResult,
                                           CriticResult criticResult,
                                           Long durationMs) {
        return ExecutionResult.builder()
                .success(true)
                .finalTaskStatus(status)
                .agentResult(agentResult)
                .criticResult(criticResult)
                .durationMs(durationMs)
                .message("Task executed and output ACCEPTED by Critic")
                .build();
    }

    public static ExecutionResult rejected(TaskStatus status,
                                           AgentExecutionResult agentResult,
                                           CriticResult criticResult,
                                           Long durationMs) {
        return ExecutionResult.builder()
                .success(false)
                .finalTaskStatus(status)
                .agentResult(agentResult)
                .criticResult(criticResult)
                .durationMs(durationMs)
                .message("Task executed but output was REJECTED by Critic")
                .build();
    }

    public static ExecutionResult blocked(String message) {
        return ExecutionResult.builder()
                .success(false)
                .finalTaskStatus(TaskStatus.BLOCKED)
                .durationMs(0L)
                .message(message)
                .build();
    }
}
