package com.incidentmind.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetadata {

    private String name;
    private String type;
    private String description;
    private List<String> requiredParameters;
    private Map<String, String> parameterDescriptions;
}
