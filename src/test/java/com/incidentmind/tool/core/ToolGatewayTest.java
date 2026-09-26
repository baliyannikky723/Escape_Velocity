package com.incidentmind.tool.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.filter.CorrelationContext;
import com.incidentmind.tool.entity.ToolCall;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.failure.FailureInjectionProperties;
import com.incidentmind.tool.failure.FailureInjector;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import com.incidentmind.tool.repository.ToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolGatewayTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolCallRepository toolCallRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private Tool mockTool;

    private FailureInjectionProperties failureInjectionProperties;
    private FailureInjector failureInjector;
    private DefaultToolGateway toolGateway;

    @BeforeEach
    void setUp() {
        failureInjectionProperties = new FailureInjectionProperties();
        failureInjector = new FailureInjector(failureInjectionProperties);
        toolGateway = new DefaultToolGateway(toolRegistry, toolCallRepository, auditService, failureInjector);
        CorrelationContext.setCorrelationId(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("ToolGateway: successful execution creates ToolCall (STARTED -> SUCCESS), Audit events, and redacts secrets")
    void invokeTool_Success() {
        String toolName = "github.get_repository";
        Map<String, Object> params = new HashMap<>();
        params.put("owner", "octocat");
        params.put("repository", "Hello-World");
        params.put("token", "secret-gh-token");

        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ToolRequest request = ToolRequest.builder()
                .toolName(toolName)
                .parameters(params)
                .investigationId(investigationId)
                .taskId(taskId)
                .build();

        when(toolRegistry.getTool(toolName)).thenReturn(mockTool);
        when(mockTool.getName()).thenReturn(toolName);
        when(mockTool.getType()).thenReturn("EXTERNAL_API");

        ToolResult toolExecutionResult = ToolResult.success(
                toolName,
                "EXTERNAL_API",
                200,
                Map.of("name", "Hello-World", "privateKey", "secret-key"),
                Map.of("X-RateLimit-Remaining", "59"),
                120L
        );
        when(mockTool.execute(any(), any())).thenReturn(toolExecutionResult);

        when(toolCallRepository.save(any(ToolCall.class))).thenAnswer(inv -> {
            ToolCall tc = inv.getArgument(0);
            if (tc.getId() == null) {
                tc.setId(UUID.randomUUID());
            }
            return tc;
        });

        ToolResult finalResult = toolGateway.invokeTool(request);

        assertThat(finalResult.isSuccess()).isTrue();
        assertThat(finalResult.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(finalResult.getHttpStatus()).isEqualTo(200);
        assertThat(finalResult.getToolCallId()).isNotNull();
        assertThat(finalResult.getCorrelationId()).isEqualTo(CorrelationContext.getCorrelationIdAsUuid());

        // Verify request payload was sanitized in result
        assertThat(finalResult.getRequestPayload().get("token")).isEqualTo("[REDACTED]");

        // Verify ToolCall was saved at least twice (initial + final)
        ArgumentCaptor<ToolCall> toolCallCaptor = ArgumentCaptor.forClass(ToolCall.class);
        verify(toolCallRepository, atLeastOnce()).save(toolCallCaptor.capture());

        ToolCall lastSavedToolCall = toolCallCaptor.getValue();
        assertThat(lastSavedToolCall.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(lastSavedToolCall.getHttpStatus()).isEqualTo(200);
        assertThat(lastSavedToolCall.getRequestPayload().get("token")).isEqualTo("[REDACTED]");
        assertThat(lastSavedToolCall.getResponsePayload().get("privateKey")).isEqualTo("[REDACTED]");

        // Verify Audit events recorded
        verify(auditService).recordEvent(
                eq(investigationId),
                eq(taskId),
                any(),
                eq(AuditEventType.TOOL_CALL_STARTED),
                eq(ActorType.TOOL),
                eq(toolName),
                any(),
                any()
        );

        verify(auditService).recordEvent(
                eq(investigationId),
                eq(taskId),
                any(),
                eq(AuditEventType.TOOL_CALL_COMPLETED),
                eq(ActorType.TOOL),
                eq(toolName),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ToolGateway: failure execution updates ToolCall to FAILED and records TOOL_CALL_FAILED audit event")
    void invokeTool_Failure() {
        String toolName = "github.get_repository";
        ToolRequest request = ToolRequest.builder()
                .toolName(toolName)
                .parameters(Map.of("owner", "nonexistent", "repository", "repo"))
                .build();

        when(toolRegistry.getTool(toolName)).thenReturn(mockTool);
        when(mockTool.getName()).thenReturn(toolName);
        when(mockTool.getType()).thenReturn("EXTERNAL_API");

        ToolResult failureResult = ToolResult.failure(
                toolName,
                "EXTERNAL_API",
                ToolCallStatus.FAILED,
                404,
                "NOT_FOUND",
                "Repository not found",
                ErrorClassification.HTTP_4XX,
                Map.of("message", "Not Found"),
                Map.of(),
                80L
        );
        when(mockTool.execute(any(), any())).thenReturn(failureResult);

        when(toolCallRepository.save(any(ToolCall.class))).thenAnswer(inv -> inv.getArgument(0));

        ToolResult result = toolGateway.invokeTool(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(result.getHttpStatus()).isEqualTo(404);
        assertThat(result.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_4XX);

        verify(auditService).recordEvent(
                any(),
                any(),
                any(),
                eq(AuditEventType.TOOL_CALL_FAILED),
                eq(ActorType.TOOL),
                eq(toolName),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ToolGateway: failure injection HTTP_500 intercepts call deterministically")
    void invokeTool_FailureInjection_HTTP_500() {
        String toolName = "github.get_recent_commits";
        failureInjectionProperties.setEnabled(true);
        failureInjectionProperties.setRules(Map.of("github.get_recent_commits", "HTTP_500"));

        ToolRequest request = ToolRequest.builder()
                .toolName(toolName)
                .parameters(Map.of("owner", "octocat", "repository", "Hello-World"))
                .build();

        when(toolRegistry.getTool(toolName)).thenReturn(mockTool);
        when(mockTool.getName()).thenReturn(toolName);
        when(mockTool.getType()).thenReturn("EXTERNAL_API");
        when(toolCallRepository.save(any(ToolCall.class))).thenAnswer(inv -> inv.getArgument(0));

        ToolResult result = toolGateway.invokeTool(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(result.getHttpStatus()).isEqualTo(500);
        assertThat(result.getErrorCode()).isEqualTo("HTTP_500");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_5XX);

        verify(auditService).recordEvent(
                any(),
                any(),
                any(),
                eq(AuditEventType.TOOL_CALL_FAILED),
                eq(ActorType.TOOL),
                eq(toolName),
                any(),
                any()
        );
    }
}
