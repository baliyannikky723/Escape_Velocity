package com.incidentmind.tool.dto;

import com.incidentmind.tool.model.ToolMetadata;
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
public class ToolMetadataResponse {

    private String name;
    private String type;
    private String description;
    private List<String> requiredParameters;
    private Map<String, String> parameterDescriptions;

    public static ToolMetadataResponse fromMetadata(ToolMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return ToolMetadataResponse.builder()
                .name(metadata.getName())
                .type(metadata.getType())
                .description(metadata.getDescription())
                .requiredParameters(metadata.getRequiredParameters())
                .parameterDescriptions(metadata.getParameterDescriptions())
                .build();
    }
}
