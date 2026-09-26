package com.incidentmind.tool.model;

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
public class ToolRequest {

    private String toolName;

    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    private UUID agentRunId;
    private UUID investigationId;
    private UUID taskId;
    private Integer timeoutMs;

    public Object getParameter(String key) {
        return parameters != null ? parameters.get(key) : null;
    }

    public String getStringParameter(String key) {
        Object val = getParameter(key);
        return val != null ? val.toString() : null;
    }

    public Integer getIntegerParameter(String key, Integer defaultValue) {
        Object val = getParameter(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
