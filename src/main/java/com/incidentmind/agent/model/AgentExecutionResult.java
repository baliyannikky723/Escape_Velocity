package com.incidentmind.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.agent.entity.AgentRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentExecutionResult {

    private boolean success;
    private AgentRunStatus status;

    @Builder.Default
    private Map<String, Object> outputPayload = new HashMap<>();

    @Builder.Default
    private List<EvidenceDraft> evidenceDrafts = new ArrayList<>();

    @Builder.Default
    private List<String> hypotheses = new ArrayList<>();

    @Builder.Default
    private List<String> recommendedTaskTypes = new ArrayList<>();

    private String errorCode;
    private String errorMessage;
    private Long durationMs;

    public static AgentExecutionResult success(Map<String, Object> outputPayload,
                                               List<EvidenceDraft> evidenceDrafts,
                                               List<String> hypotheses,
                                               List<String> recommendedTaskTypes,
                                               Long durationMs) {
        return AgentExecutionResult.builder()
                .success(true)
                .status(AgentRunStatus.COMPLETED)
                .outputPayload(outputPayload != null ? outputPayload : new HashMap<>())
                .evidenceDrafts(evidenceDrafts != null ? evidenceDrafts : new ArrayList<>())
                .hypotheses(hypotheses != null ? hypotheses : new ArrayList<>())
                .recommendedTaskTypes(recommendedTaskTypes != null ? recommendedTaskTypes : new ArrayList<>())
                .durationMs(durationMs)
                .build();
    }

    public static AgentExecutionResult failure(AgentRunStatus status,
                                               String errorCode,
                                               String errorMessage,
                                               Map<String, Object> outputPayload,
                                               Long durationMs) {
        return AgentExecutionResult.builder()
                .success(false)
                .status(status != null ? status : AgentRunStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .outputPayload(outputPayload != null ? outputPayload : new HashMap<>())
                .durationMs(durationMs)
                .build();
    }
}
