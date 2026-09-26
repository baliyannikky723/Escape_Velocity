package com.incidentmind.stopping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoppingResult {

    private StoppingDecision decision;
    private boolean shouldStop;
    private String reason;
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    public static StoppingResult continueInvestigation(String reason) {
        return StoppingResult.builder()
                .decision(StoppingDecision.CONTINUE)
                .shouldStop(false)
                .reason(reason != null ? reason : "Investigation conditions permit continued execution")
                .build();
    }

    public static StoppingResult stopSuccess(String reason, Map<String, Object> details) {
        return StoppingResult.builder()
                .decision(StoppingDecision.STOP_SUCCESS)
                .shouldStop(true)
                .reason(reason != null ? reason : "Investigation completed successfully with verified evidence")
                .details(details != null ? details : new HashMap<>())
                .build();
    }

    public static StoppingResult stopLimitReached(String reason, Map<String, Object> details) {
        return StoppingResult.builder()
                .decision(StoppingDecision.STOP_LIMIT_REACHED)
                .shouldStop(true)
                .reason(reason != null ? reason : "Maximum planned task limit reached")
                .details(details != null ? details : new HashMap<>())
                .build();
    }

    public static StoppingResult stopTimeout(String reason, Map<String, Object> details) {
        return StoppingResult.builder()
                .decision(StoppingDecision.STOP_TIMEOUT)
                .shouldStop(true)
                .reason(reason != null ? reason : "Maximum investigation runtime exceeded")
                .details(details != null ? details : new HashMap<>())
                .build();
    }

    public static StoppingResult stopBlocked(String reason) {
        return StoppingResult.builder()
                .decision(StoppingDecision.STOP_BLOCKED)
                .shouldStop(true)
                .reason(reason != null ? reason : "All remaining investigation tasks are blocked")
                .build();
    }

    public static StoppingResult humanApprovalRequired(String reason) {
        return StoppingResult.builder()
                .decision(StoppingDecision.HUMAN_APPROVAL_REQUIRED)
                .shouldStop(true)
                .reason(reason != null ? reason : "Investigation paused awaiting human engineer approval for sensitive action")
                .build();
    }
}
