package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.critic.dto.CriticEvaluationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriticSummaryResponse {

    private int totalEvaluations;
    private int acceptedCount;
    private int rejectedCount;
    private int inconclusiveCount;
    private int humanApprovalCount;
    @Builder.Default
    private List<UUID> rejectedTaskIds = new ArrayList<>();
    @Builder.Default
    private List<String> rejectionReasons = new ArrayList<>();
    private boolean replanTriggeredByCritic;
    @Builder.Default
    private List<CriticEvaluationResponse> evaluations = new ArrayList<>();
}
