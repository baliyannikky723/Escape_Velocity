package com.incidentmind.tool.core;

import com.incidentmind.tool.model.ToolMetadata;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {

    Optional<Tool> findTool(String name);

    Tool getTool(String name);

    List<ToolMetadata> getAvailableTools();

    boolean containsTool(String name);
}
