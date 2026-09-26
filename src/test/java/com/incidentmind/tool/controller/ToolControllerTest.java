package com.incidentmind.tool.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.common.exception.GlobalExceptionHandler;
import com.incidentmind.common.filter.CorrelationIdFilter;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.core.ToolRegistry;
import com.incidentmind.tool.dto.InvokeToolApiRequest;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ToolMetadata;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ToolGateway toolGateway;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private ToolController toolController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(toolController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/tools returns list of registered tool metadata")
    void getAvailableTools_ReturnsList() throws Exception {
        ToolMetadata meta1 = ToolMetadata.builder()
                .name("github.get_repository")
                .type("EXTERNAL_API")
                .description("Get repository metadata")
                .requiredParameters(List.of("owner", "repository"))
                .build();

        when(toolRegistry.getAvailableTools()).thenReturn(List.of(meta1));

        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("github.get_repository")))
                .andExpect(jsonPath("$[0].type", is("EXTERNAL_API")))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("POST /api/v1/tools/invoke executes tool via ToolGateway and returns ToolResult")
    void invokeTool_Success() throws Exception {
        InvokeToolApiRequest request = InvokeToolApiRequest.builder()
                .toolName("github.get_repository")
                .parameters(Map.of("owner", "octocat", "repository", "Hello-World"))
                .build();

        UUID toolCallId = UUID.randomUUID();
        ToolResult result = ToolResult.success(
                "github.get_repository",
                "EXTERNAL_API",
                200,
                Map.of("name", "Hello-World", "stars", 80),
                Map.of("X-RateLimit-Remaining", "59"),
                100L
        );
        result.setToolCallId(toolCallId);

        when(toolGateway.invokeTool(any(ToolRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/tools/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.toolName", is("github.get_repository")))
                .andExpect(jsonPath("$.httpStatus", is(200)))
                .andExpect(jsonPath("$.toolCallId", is(toolCallId.toString())));

        verify(toolGateway).invokeTool(any(ToolRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/tools/invoke rejects request with missing toolName")
    void invokeTool_MissingToolName_Returns400() throws Exception {
        String invalidJson = """
                {
                    "toolName": "",
                    "parameters": {}
                }
                """;

        mockMvc.perform(post("/api/v1/tools/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }
}
