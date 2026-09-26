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
public class AiPlanResponse {

    private UUID investigationId;
    private String decision;
    private String reason;
    @Builder.Default
    private List<String> knownFacts = new ArrayList<>();
    @Builder.Default
    private List<String> unknowns = new ArrayList<>();
    @Builder.Default
    private List<AiPlannedActionDto> actions = new ArrayList<>();
    private boolean isEscalated;
    private String escalationReason;
    @Builder.Default
    private List<String> allowedHumanActions = new ArrayList<>();
    private String llmProvider;
    private String llmModel;
    private double durationMs;
    private int tokensUsed;
    private boolean isFallback;
}
