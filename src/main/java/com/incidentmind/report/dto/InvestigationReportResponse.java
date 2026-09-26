package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.blackboard.dto.InvestigationMetricsResponse;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.report.model.ConclusionType;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvestigationReportResponse {

    private UUID investigationId;
    private UUID incidentId;
    private String incidentTitle;
    private InvestigationStatus status;
    private String finalConclusion;
    private ConclusionType conclusionType;
    private String confidenceReasoning;
    @Builder.Default
    private List<String> keyFindings = new ArrayList<>();
    @Builder.Default
    private List<EvidenceResponse> evidence = new ArrayList<>();
    @Builder.Default
    private List<String> hypotheses = new ArrayList<>();
    @Builder.Default
    private List<String> unresolvedQuestions = new ArrayList<>();
    @Builder.Default
    private List<String> recommendedNextActions = new ArrayList<>();
    private CriticSummaryResponse criticSummary;
    private RecoverySummaryResponse recoverySummary;
    private StoppingSummaryDto stopping;
    private InvestigationMetricsResponse metrics;
    @Builder.Default
    private List<TimelineEventDto> timeline = new ArrayList<>();
    private Instant generatedAt;
}
