package com.incidentmind.critic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriticEvaluationResponse {

    private UUID taskId;
    private CriticDecision decision;
    private List<String> reasons;
    private List<String> failedChecks;
    private List<String> acceptedClaims;
    private List<String> rejectedClaims;
    private List<String> requiredEvidence;
    private Double confidence;
    private String recommendedNextAction;
    private Instant evaluatedAt;

    public static CriticEvaluationResponse fromModel(CriticResult result) {
        if (result == null) return null;
        return CriticEvaluationResponse.builder()
                .taskId(result.getTaskId())
                .decision(result.getDecision())
                .reasons(result.getReasons())
                .failedChecks(result.getFailedChecks())
                .acceptedClaims(result.getAcceptedClaims())
                .rejectedClaims(result.getRejectedClaims())
                .requiredEvidence(result.getRequiredEvidence())
                .confidence(result.getConfidence())
                .recommendedNextAction(result.getRecommendedNextAction())
                .evaluatedAt(result.getEvaluatedAt())
                .build();
    }
}
