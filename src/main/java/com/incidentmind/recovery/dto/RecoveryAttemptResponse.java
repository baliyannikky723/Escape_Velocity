package com.incidentmind.recovery.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecoveryAttemptResponse {

    private UUID id;
    private UUID investigationId;
    private UUID taskId;
    private UUID toolCallId;
    private RecoveryType recoveryType;
    private String reason;
    private Integer attemptNumber;
    private RecoveryStatus status;
    private Map<String, Object> details;
    private Instant createdAt;
    private Instant completedAt;

    public static RecoveryAttemptResponse fromEntity(RecoveryAttempt attempt) {
        if (attempt == null) return null;
        return RecoveryAttemptResponse.builder()
                .id(attempt.getId())
                .investigationId(attempt.getInvestigationId())
                .taskId(attempt.getTaskId())
                .toolCallId(attempt.getToolCallId())
                .recoveryType(attempt.getRecoveryType())
                .reason(attempt.getReason())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .details(attempt.getDetails())
                .createdAt(attempt.getCreatedAt())
                .completedAt(attempt.getCompletedAt())
                .build();
    }
}
