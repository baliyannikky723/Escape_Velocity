package com.incidentmind.tool.core;

import com.incidentmind.tool.model.ToolExecutionContext;
import com.incidentmind.tool.model.ToolMetadata;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;

public interface Tool {

    String getName();

    String getType();

    String getDescription();

    ToolMetadata getMetadata();

    ToolResult execute(ToolRequest request, ToolExecutionContext context);
}
