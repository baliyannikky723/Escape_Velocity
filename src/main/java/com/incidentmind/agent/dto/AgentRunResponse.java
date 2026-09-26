package com.incidentmind.agent.dto;

import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.entity.AgentRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResponse {

    private UUID id;
    private UUID taskId;
    private String agentType;
    private AgentRunStatus status;
    private String modelName;
    private Map<String, Object> inputPayload;
    private Map<String, Object> outputPayload;
    private String errorCode;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private Instant createdAt;

    public static AgentRunResponse fromEntity(AgentRun run) {
        if (run == null) {
            return null;
        }
        return AgentRunResponse.builder()
                .id(run.getId())
                .taskId(run.getTaskId())
                .agentType(run.getAgentType())
                .status(run.getStatus())
                .modelName(run.getModelName())
                .inputPayload(run.getInputPayload())
                .outputPayload(run.getOutputPayload())
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .durationMs(run.getDurationMs())
                .createdAt(run.getCreatedAt())
                .build();
    }
}
