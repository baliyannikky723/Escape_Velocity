package com.incidentmind.critic.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticResult {

    private CriticDecision decision;
    @Builder.Default
    private List<String> reasons = new ArrayList<>();
    @Builder.Default
    private List<String> failedChecks = new ArrayList<>();
    @Builder.Default
    private List<String> acceptedClaims = new ArrayList<>();
    @Builder.Default
    private List<String> rejectedClaims = new ArrayList<>();
    @Builder.Default
    private List<String> requiredEvidence = new ArrayList<>();
    private Double confidence;
    private String recommendedNextAction;
    @Builder.Default
    private Instant evaluatedAt = Instant.now();
    private UUID taskId;
    private UUID agentRunId;

    public boolean isAccepted() {
        return decision == CriticDecision.ACCEPT;
    }

    public boolean isRejected() {
        return decision == CriticDecision.REJECT;
    }

    public boolean isHumanApprovalRequired() {
        return decision == CriticDecision.HUMAN_APPROVAL_REQUIRED;
    }

    public static CriticResult accept(UUID taskId, List<String> acceptedClaims, String reason, Double confidence) {
        return CriticResult.builder()
                .decision(CriticDecision.ACCEPT)
                .taskId(taskId)
                .acceptedClaims(acceptedClaims != null ? acceptedClaims : List.of())
                .reasons(reason != null ? List.of(reason) : List.of("Output verified and grounded in evidence"))
                .confidence(confidence != null ? confidence : 0.95)
                .recommendedNextAction("PROCEED")
                .evaluatedAt(Instant.now())
                .build();
    }

    public static CriticResult reject(UUID taskId,
                                      List<String> failedChecks,
                                      List<String> rejectedClaims,
                                      String reason,
                                      String recommendedNextAction) {
        return CriticResult.builder()
                .decision(CriticDecision.REJECT)
                .taskId(taskId)
                .failedChecks(failedChecks != null ? failedChecks : List.of())
                .rejectedClaims(rejectedClaims != null ? rejectedClaims : List.of())
                .reasons(reason != null ? List.of(reason) : List.of("Output failed validation checks"))
                .confidence(0.2)
                .recommendedNextAction(recommendedNextAction != null ? recommendedNextAction : "REPLAN")
                .evaluatedAt(Instant.now())
                .build();
    }

    public static CriticResult inconclusive(UUID taskId, List<String> requiredEvidence, String reason) {
        return CriticResult.builder()
                .decision(CriticDecision.INCONCLUSIVE)
                .taskId(taskId)
                .requiredEvidence(requiredEvidence != null ? requiredEvidence : List.of())
                .reasons(reason != null ? List.of(reason) : List.of("Evidence collected is insufficient for definitive conclusion"))
                .confidence(0.5)
                .recommendedNextAction("GATHER_MORE_EVIDENCE")
                .evaluatedAt(Instant.now())
                .build();
    }

    public static CriticResult humanApprovalRequired(UUID taskId, String proposedAction, String reason) {
        return CriticResult.builder()
                .decision(CriticDecision.HUMAN_APPROVAL_REQUIRED)
                .taskId(taskId)
                .reasons(List.of("Proposed action requires human engineer approval: " + proposedAction, reason != null ? reason : ""))
                .failedChecks(List.of("SAFETY_POLICY_CHECK"))
                .recommendedNextAction("AWAIT_HUMAN_APPROVAL")
                .evaluatedAt(Instant.now())
                .build();
    }
}
