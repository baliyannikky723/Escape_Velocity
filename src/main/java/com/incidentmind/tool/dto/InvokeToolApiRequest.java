package com.incidentmind.tool.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeToolApiRequest {

    @NotBlank(message = "toolName is required")
    private String toolName;

    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    private UUID agentRunId;
    private UUID investigationId;
    private UUID taskId;
    private Integer timeoutMs;
}
