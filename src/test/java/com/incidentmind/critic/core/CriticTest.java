package com.incidentmind.critic.core;

import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.critic.model.CriticContext;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CriticTest {

    @Mock
    private AuditService auditService;

    private DefaultInvestigationCritic critic;

    @BeforeEach
    void setUp() {
        critic = new DefaultInvestigationCritic(auditService);
    }

    @Test
    @DisplayName("evaluate: strictly grounded factual evidence is ACCEPTED")
    void evaluate_GroundedEvidence_Accepted() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder().id(taskId).taskType("CHANGE_ANALYSIS").build();
        Investigation investigation = Investigation.builder().id(invId).build();

        EvidenceDraft draft = EvidenceDraft.builder()
                .sourceType("GITHUB")
                .sourceReference("repo:octocat/Hello-World#commit:abc123")
                .claim("Recent commit abc123 pushed before incident with message: Fix checkout validation")
                .confidence(new BigDecimal("0.90"))
                .build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("summary", "Found recent commit"),
                List.of(draft),
                List.of(),
                List.of(),
                100L
        );

        CriticContext context = CriticContext.builder()
                .investigation(investigation)
                .task(task)
                .agentResult(agentResult)
                .correlationId(correlationId)
                .build();

        CriticResult result = critic.evaluate(context);

        assertThat(result.getDecision()).isEqualTo(CriticDecision.ACCEPT);
        assertThat(result.isAccepted()).isTrue();

        verify(auditService).recordEvent(
                eq(invId),
                eq(taskId),
                any(),
                eq(AuditEventType.CRITIC_ACCEPTED),
                eq(ActorType.SYSTEM),
                eq("investigation-critic"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("evaluate: unsupported causal assertion triggers REJECT decision")
    void evaluate_UnsupportedCausalClaim_Rejected() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder().id(taskId).taskType("CHANGE_ANALYSIS").build();
        Investigation investigation = Investigation.builder().id(invId).build();

        EvidenceDraft ungroundedDraft = EvidenceDraft.builder()
                .sourceType("GITHUB")
                .sourceReference("repo:octocat/Hello-World#commit:abc123")
                .claim("Commit abc123 caused the incident and is the root cause")
                .confidence(new BigDecimal("0.90"))
                .build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("causalClaim", "Commit abc123 caused the incident"),
                List.of(ungroundedDraft),
                List.of(),
                List.of(),
                100L
        );

        CriticContext context = CriticContext.builder()
                .investigation(investigation)
                .task(task)
                .agentResult(agentResult)
                .correlationId(correlationId)
                .build();

        CriticResult result = critic.evaluate(context);

        assertThat(result.getDecision()).isEqualTo(CriticDecision.REJECT);
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getFailedChecks()).contains("UNSUPPORTED_CAUSAL_CLAIM");

        verify(auditService).recordEvent(
                eq(invId),
                eq(taskId),
                any(),
                eq(AuditEventType.CRITIC_REJECTED),
                eq(ActorType.SYSTEM),
                eq("investigation-critic"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("evaluate: proposed automated production rollback triggers HUMAN_APPROVAL_REQUIRED")
    void evaluate_SensitiveProductionAction_HumanApprovalRequired() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder().id(taskId).taskType("INCIDENT_TRIAGE").build();
        Investigation investigation = Investigation.builder().id(invId).build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("proposedAction", "ROLLBACK deployment to previous version immediately"),
                List.of(),
                List.of(),
                List.of(),
                80L
        );

        CriticContext context = CriticContext.builder()
                .investigation(investigation)
                .task(task)
                .agentResult(agentResult)
                .correlationId(correlationId)
                .build();

        CriticResult result = critic.evaluate(context);

        assertThat(result.getDecision()).isEqualTo(CriticDecision.HUMAN_APPROVAL_REQUIRED);
        assertThat(result.isHumanApprovalRequired()).isTrue();

        verify(auditService).recordEvent(
                eq(invId),
                eq(taskId),
                any(),
                eq(AuditEventType.HUMAN_APPROVAL_REQUIRED),
                eq(ActorType.SYSTEM),
                eq("investigation-critic"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("evaluate: incomplete agent result triggers INCONCLUSIVE")
    void evaluate_IncompleteAgentResult_Inconclusive() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder().id(taskId).taskType("DEPENDENCY_ANALYSIS").build();
        Investigation investigation = Investigation.builder().id(invId).build();

        AgentExecutionResult failedAgentResult = AgentExecutionResult.failure(
                AgentRunStatus.FAILED,
                "TELEMETRY_UNAVAILABLE",
                "Downstream telemetry metrics not collected",
                Map.of(),
                100L
        );

        CriticContext context = CriticContext.builder()
                .investigation(investigation)
                .task(task)
                .agentResult(failedAgentResult)
                .correlationId(UUID.randomUUID())
                .build();

        CriticResult result = critic.evaluate(context);

        assertThat(result.getDecision()).isEqualTo(CriticDecision.INCONCLUSIVE);
        assertThat(result.getRequiredEvidence()).isNotEmpty();
    }
}
