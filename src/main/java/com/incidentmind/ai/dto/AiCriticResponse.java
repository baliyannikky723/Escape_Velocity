package com.incidentmind.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class AiCriticResponse {

    private UUID investigationId;
    private UUID taskId;
    private String decision;
    private String reason;
    @Builder.Default
    private List<String> supportedClaims = new ArrayList<>();
    @Builder.Default
    private List<String> unsupportedClaims = new ArrayList<>();
    @Builder.Default
    private List<String> missingEvidence = new ArrayList<>();
    @Builder.Default
    private List<String> recommendedFollowUp = new ArrayList<>();
    private boolean requiresHumanReview;
    private String proposedAction;
    @Builder.Default
    private List<String> allowedHumanActions = new ArrayList<>();
    private String llmProvider;
    private String llmModel;
    private double durationMs;
    private int tokensUsed;
}
