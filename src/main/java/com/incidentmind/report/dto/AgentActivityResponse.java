package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.critic.model.CriticDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentActivityResponse {

    private UUID agentRunId;
    private UUID taskId;
    private String agentType;
    private AgentRunStatus status;
    private Long durationMs;
    private Instant startedAt;
    private Instant completedAt;
    private CriticDecision criticDecision;
    private int evidenceProduced;
    private int hypothesesProduced;
    private String errorCode;
    private String errorMessage;

    public static AgentActivityResponse fromEntity(AgentRun run, CriticDecision decision) {
        if (run == null) return null;

        int evidenceCount = 0;
        int hypothesesCount = 0;
        if (run.getOutputPayload() != null) {
            if (run.getOutputPayload().get("evidenceCount") instanceof Number n) {
                evidenceCount = n.intValue();
            }
            if (run.getOutputPayload().get("hypotheses") instanceof java.util.List<?> list) {
                hypothesesCount = list.size();
            }
        }

        return AgentActivityResponse.builder()
                .agentRunId(run.getId())
                .taskId(run.getTaskId())
                .agentType(run.getAgentType())
                .status(run.getStatus())
                .durationMs(run.getDurationMs())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .criticDecision(decision)
                .evidenceProduced(evidenceCount)
                .hypothesesProduced(hypothesesCount)
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .build();
    }
}
