package com.incidentmind.recovery.model;

import com.incidentmind.recovery.entity.RecoveryType;
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
public class RecoveryDecision {

    private RecoveryType recoveryType;
    private boolean shouldRecover;
    private long backoffMs;
    private String reason;
    private String targetToolName;

    @Builder.Default
    private Map<String, Object> decisionData = new HashMap<>();

    public static RecoveryDecision retry(String reason, long backoffMs, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.RETRY)
                .shouldRecover(true)
                .backoffMs(backoffMs)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }

    public static RecoveryDecision backoff(String reason, long backoffMs, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.BACKOFF)
                .shouldRecover(true)
                .backoffMs(backoffMs)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }

    public static RecoveryDecision fallbackTool(String fallbackToolName, String reason, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.FALLBACK_TOOL)
                .shouldRecover(true)
                .targetToolName(fallbackToolName)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }

    public static RecoveryDecision replan(String reason, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.REPLAN)
                .shouldRecover(false)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }

    public static RecoveryDecision abort(String reason, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.ABORT)
                .shouldRecover(false)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }

    public static RecoveryDecision skip(String reason, Map<String, Object> data) {
        return RecoveryDecision.builder()
                .recoveryType(RecoveryType.SKIP)
                .shouldRecover(false)
                .reason(reason)
                .decisionData(data != null ? data : new HashMap<>())
                .build();
    }
}
