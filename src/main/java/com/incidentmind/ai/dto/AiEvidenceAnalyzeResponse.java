package com.incidentmind.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiEvidenceAnalyzeResponse {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtractedClaimDto {
        private String claim;
        private String type;
        private String source;
        private String sourceReference;
        private Double confidence;
        private Map<String, Object> rawData;
    }

    @Builder.Default
    private List<ExtractedClaimDto> claims = new ArrayList<>();
    @Builder.Default
    private List<String> unsupportedClaims = new ArrayList<>();
    @Builder.Default
    private List<String> missingInformation = new ArrayList<>();
    private double durationMs;
    private int tokensUsed;
    private String llmProvider;
    private String llmModel;
}
