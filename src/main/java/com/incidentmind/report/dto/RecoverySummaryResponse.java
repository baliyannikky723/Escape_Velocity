package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.recovery.dto.RecoveryAttemptResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecoverySummaryResponse {

    private int totalAttempts;
    private int successfulRecoveries;
    private int exhaustedRecoveries;
    private int retryCount;
    private int fallbackCount;
    private int replanCount;
    @Builder.Default
    private List<RecoveryAttemptResponse> attempts = new ArrayList<>();
}
