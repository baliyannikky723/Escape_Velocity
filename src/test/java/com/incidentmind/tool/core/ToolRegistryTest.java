package com.incidentmind.tool.core;

import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.tool.model.ToolMetadata;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryTest {

    private Tool mockTool1;
    private Tool mockTool2;
    private DefaultToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        mockTool1 = mock(Tool.class);
        when(mockTool1.getName()).thenReturn("github.get_repository");
        when(mockTool1.getType()).thenReturn("EXTERNAL_API");
        when(mockTool1.getMetadata()).thenReturn(ToolMetadata.builder()
                .name("github.get_repository")
                .type("EXTERNAL_API")
                .description("Get repository info")
                .build());

        mockTool2 = mock(Tool.class);
        when(mockTool2.getName()).thenReturn("github.get_recent_commits");
        when(mockTool2.getType()).thenReturn("EXTERNAL_API");
        when(mockTool2.getMetadata()).thenReturn(ToolMetadata.builder()
                .name("github.get_recent_commits")
                .type("EXTERNAL_API")
                .description("Get recent commits")
                .build());

        toolRegistry = new DefaultToolRegistry(List.of(mockTool1, mockTool2));
    }

    @Test
    @DisplayName("Tool Registry: returns registered tool when searched by name")
    void findTool_ExistingTool_ReturnsTool() {
        Optional<Tool> tool = toolRegistry.findTool("github.get_repository");
        assertThat(tool).isPresent();
        assertThat(tool.get().getName()).isEqualTo("github.get_repository");
        assertThat(toolRegistry.containsTool("github.get_repository")).isTrue();
    }

    @Test
    @DisplayName("Tool Registry: rejects and throws ResourceNotFoundException for unknown tool")
    void getTool_UnknownTool_ThrowsException() {
        assertThatThrownBy(() -> toolRegistry.getTool("unknown.tool"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown.tool");
    }

    @Test
    @DisplayName("Tool Registry: returns all available tool metadata")
    void getAvailableTools_ReturnsMetadataList() {
        List<ToolMetadata> availableTools = toolRegistry.getAvailableTools();
        assertThat(availableTools).hasSize(2);
        assertThat(availableTools.stream().map(ToolMetadata::getName).toList())
                .containsExactlyInAnyOrder("github.get_repository", "github.get_recent_commits");
    }
}
