package com.incidentmind.tool.core;

import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;

public interface ToolGateway {

    ToolResult invokeTool(ToolRequest request);
}
