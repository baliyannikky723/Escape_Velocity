package com.incidentmind.recovery.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.recovery.model.RecoveryResult;
import com.incidentmind.recovery.policy.BackoffStrategy;
import com.incidentmind.recovery.policy.RecoveryPolicy;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryEngineTest {

    @Mock
    private RecoveryPolicy recoveryPolicy;

    @Mock
    private RecoveryAttemptRepository recoveryAttemptRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ToolGateway toolGateway;

    @Mock
    private BackoffStrategy backoffStrategy;

    private DefaultRecoveryEngine recoveryEngine;

    @BeforeEach
    void setUp() {
        recoveryEngine = new DefaultRecoveryEngine(
                recoveryPolicy,
                recoveryAttemptRepository,
                auditService,
                toolGateway,
                backoffStrategy
        );
    }

    @Test
    @DisplayName("decide: evaluates policy and records RECOVERY_DECISION audit event")
    void decide_Success() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        RecoveryContext context = RecoveryContext.builder()
                .investigationId(investigationId)
                .taskId(taskId)
                .correlationId(correlationId)
                .attemptNumber(1)
                .maxAttempts(3)
                .httpStatus(503)
                .build();

        RecoveryDecision expectedDecision = RecoveryDecision.retry("Transient 503", 200L, Map.of());
        when(recoveryPolicy.evaluate(context)).thenReturn(expectedDecision);

        RecoveryDecision decision = recoveryEngine.decide(context);

        assertThat(decision).isNotNull();
        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.RETRY);

        verify(auditService).recordEvent(
                eq(investigationId),
                eq(taskId),
                any(),
                eq(AuditEventType.RECOVERY_DECISION),
                eq(ActorType.SYSTEM),
                eq("recovery-engine"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("recover: executes successful retry and persists SUCCESS recovery attempt")
    void recover_SuccessfulRetry() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID toolCallId = UUID.randomUUID();

        ToolRequest toolRequest = ToolRequest.builder()
                .toolName("github.get_recent_commits")
                .investigationId(investigationId)
                .taskId(taskId)
                .parameters(Map.of("owner", "octocat", "repository", "Hello-World"))
                .build();

        RecoveryContext context = RecoveryContext.builder()
                .investigationId(investigationId)
                .taskId(taskId)
                .correlationId(correlationId)
                .toolCallId(toolCallId)
                .toolName("github.get_recent_commits")
                .toolRequest(toolRequest)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = RecoveryDecision.retry("Transient 503", 100L, Map.of());

        ToolResult successfulToolResult = ToolResult.success(
                "github.get_recent_commits",
                "EXTERNAL_API",
                200,
                Map.of("items", java.util.List.of()),
                Map.of(),
                80L
        );
        successfulToolResult.setToolCallId(UUID.randomUUID());

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(toolGateway.invokeTool(toolRequest)).thenReturn(successfulToolResult);

        RecoveryResult result = recoveryEngine.recover(context, decision);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(RecoveryStatus.SUCCESS);
        assertThat(result.getRecoveredToolResult()).isNotNull();
        assertThat(result.getRecoveredToolResult().isSuccess()).isTrue();

        ArgumentCaptor<RecoveryAttempt> captor = ArgumentCaptor.forClass(RecoveryAttempt.class);
        verify(recoveryAttemptRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        RecoveryAttempt lastSaved = captor.getValue();
        assertThat(lastSaved.getStatus()).isEqualTo(RecoveryStatus.SUCCESS);

        verify(auditService).recordEvent(
                eq(investigationId),
                eq(taskId),
                any(),
                eq(AuditEventType.RECOVERY_SUCCEEDED),
                eq(ActorType.SYSTEM),
                eq("recovery-engine"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("recover: failed retry updates recovery attempt to FAILED")
    void recover_FailedRetry() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        ToolRequest toolRequest = ToolRequest.builder()
                .toolName("github.get_recent_commits")
                .investigationId(investigationId)
                .taskId(taskId)
                .parameters(Map.of())
                .build();

        RecoveryContext context = RecoveryContext.builder()
                .investigationId(investigationId)
                .taskId(taskId)
                .correlationId(correlationId)
                .toolName("github.get_recent_commits")
                .toolRequest(toolRequest)
                .attemptNumber(2)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = RecoveryDecision.retry("Transient error retry", 0L, Map.of());

        ToolResult failedToolResult = ToolResult.failure(
                "github.get_recent_commits",
                "EXTERNAL_API",
                ToolCallStatus.FAILED,
                500,
                "HTTP_500",
                "Server error",
                ErrorClassification.HTTP_5XX,
                Map.of(),
                Map.of(),
                50L
        );

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(toolGateway.invokeTool(toolRequest)).thenReturn(failedToolResult);

        RecoveryResult result = recoveryEngine.recover(context, decision);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RecoveryStatus.FAILED);

        verify(auditService).recordEvent(
                eq(investigationId),
                eq(taskId),
                any(),
                eq(AuditEventType.RECOVERY_FAILED),
                eq(ActorType.SYSTEM),
                eq("recovery-engine"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("recover: fallback tool execution invokes alternate tool via ToolGateway")
    void recover_FallbackTool() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        ToolRequest primaryRequest = ToolRequest.builder()
                .toolName("github.get_recent_commits")
                .investigationId(investigationId)
                .taskId(taskId)
                .parameters(Map.of("owner", "octocat", "repository", "Hello-World"))
                .build();

        RecoveryContext context = RecoveryContext.builder()
                .investigationId(investigationId)
                .taskId(taskId)
                .correlationId(correlationId)
                .toolName("github.get_recent_commits")
                .fallbackToolName("github.get_pull_requests")
                .toolRequest(primaryRequest)
                .attemptNumber(3)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = RecoveryDecision.fallbackTool("github.get_pull_requests", "Switching to PRs", Map.of());

        ToolResult fallbackResult = ToolResult.success(
                "github.get_pull_requests",
                "EXTERNAL_API",
                200,
                Map.of("items", java.util.List.of()),
                Map.of(),
                90L
        );

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(toolGateway.invokeTool(any(ToolRequest.class))).thenReturn(fallbackResult);

        RecoveryResult result = recoveryEngine.recover(context, decision);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecoveryType()).isEqualTo(RecoveryType.FALLBACK_TOOL);

        ArgumentCaptor<ToolRequest> requestCaptor = ArgumentCaptor.forClass(ToolRequest.class);
        verify(toolGateway).invokeTool(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getToolName()).isEqualTo("github.get_pull_requests");
    }
}
