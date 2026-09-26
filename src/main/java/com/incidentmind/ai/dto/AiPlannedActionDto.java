package com.incidentmind.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class AiPlannedActionDto {

    private String action;
    private String taskType;
    private String agentType;
    private String priority;
    private String parentTaskId;
    private String toolRequirement;
    private String suggestedTool;
    private String reason;
    @Builder.Default
    private List<String> expectedEvidence = new ArrayList<>();
}
