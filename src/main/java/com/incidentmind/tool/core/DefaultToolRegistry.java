package com.incidentmind.tool.core;

import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    public DefaultToolRegistry(List<Tool> tools) {
        if (tools != null) {
            for (Tool tool : tools) {
                registerTool(tool);
            }
        }
    }

    public void registerTool(Tool tool) {
        if (tool == null || tool.getName() == null) {
            throw new IllegalArgumentException("Tool and tool name must not be null");
        }
        toolMap.put(tool.getName(), tool);
        log.info("Registered tool: name='{}', type='{}'", tool.getName(), tool.getType());
    }

    @Override
    public Optional<Tool> findTool(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolMap.get(name));
    }

    @Override
    public Tool getTool(String name) {
        return findTool(name)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", name));
    }

    @Override
    public List<ToolMetadata> getAvailableTools() {
        return toolMap.values().stream()
                .map(Tool::getMetadata)
                .toList();
    }

    @Override
    public boolean containsTool(String name) {
        return name != null && toolMap.containsKey(name);
    }
}
