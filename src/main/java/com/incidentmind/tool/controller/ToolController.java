package com.incidentmind.tool.controller;

import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.core.ToolRegistry;
import com.incidentmind.tool.dto.InvokeToolApiRequest;
import com.incidentmind.tool.dto.ToolMetadataResponse;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private final ToolGateway toolGateway;
    private final ToolRegistry toolRegistry;

    public ToolController(ToolGateway toolGateway, ToolRegistry toolRegistry) {
        this.toolGateway = toolGateway;
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ResponseEntity<List<ToolMetadataResponse>> getAvailableTools() {
        List<ToolMetadataResponse> tools = toolRegistry.getAvailableTools().stream()
                .map(ToolMetadataResponse::fromMetadata)
                .toList();
        return ResponseEntity.ok(tools);
    }

    @PostMapping("/invoke")
    public ResponseEntity<ToolResult> invokeTool(@Valid @RequestBody InvokeToolApiRequest apiRequest) {
        ToolRequest request = ToolRequest.builder()
                .toolName(apiRequest.getToolName())
                .parameters(apiRequest.getParameters())
                .agentRunId(apiRequest.getAgentRunId())
                .investigationId(apiRequest.getInvestigationId())
                .taskId(apiRequest.getTaskId())
                .timeoutMs(apiRequest.getTimeoutMs())
                .build();

        ToolResult result = toolGateway.invokeTool(request);
        return ResponseEntity.ok(result);
    }
}
