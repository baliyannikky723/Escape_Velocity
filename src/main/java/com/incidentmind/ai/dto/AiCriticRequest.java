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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiCriticRequest {

    private UUID investigationId;
    private Map<String, Object> incident;
    private Map<String, Object> task;
    private Map<String, Object> agentOutput;
    @Builder.Default
    private List<Map<String, Object>> evidence = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> toolResults = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> previousFindings = new ArrayList<>();
}
