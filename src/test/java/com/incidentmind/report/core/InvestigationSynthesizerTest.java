package com.incidentmind.report.core;

import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.report.dto.InvestigationReportResponse;
import com.incidentmind.report.model.ConclusionType;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestigationSynthesizerTest {

    @Mock
    private InvestigationBlackboard blackboard;

    private DefaultInvestigationSynthesizer synthesizer;

    @BeforeEach
    void setUp() {
        synthesizer = new DefaultInvestigationSynthesizer();
    }

    @Test
    @DisplayName("synthesizeReport: grounded evidence and accepted critic results produces SUPPORTED_FINDING")
    void synthesizeReport_GroundedEvidence_SupportedFinding() {
        UUID invId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .title("Checkout API error rate spike")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .incidentId(incidentId)
                .status(InvestigationStatus.WAITING)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("CHANGE_ANALYSIS")
                .status(TaskStatus.COMPLETED)
                .build();

        Evidence evidence = Evidence.builder()
                .id(UUID.randomUUID())
                .sourceType("GITHUB")
                .sourceReference("repo:octocat/Hello-World#commit:abc123")
                .claim("Recent commit abc123 pushed with message: Fix checkout pipeline")
                .build();

        CriticResult criticResult = CriticResult.builder()
                .decision(CriticDecision.ACCEPT)
                .reasons(List.of("Findings grounded in repository commit payload"))
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of(criticResult));
        when(blackboard.getMetrics()).thenReturn(InvestigationMetrics.builder().build());

        InvestigationReportResponse report = synthesizer.synthesizeReport(
                incident,
                investigation,
                List.of(task),
                List.of(evidence),
                List.of(),
                List.of(),
                blackboard
        );

        assertThat(report).isNotNull();
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.SUPPORTED_FINDING);
        assertThat(report.getFinalConclusion()).contains("verified evidence supporting recent deployment activity");
        assertThat(report.getKeyFindings()).isNotEmpty();
        assertThat(report.getCriticSummary().getAcceptedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("synthesizeReport: critic rejection produces SUPPORTED_FINDING with explicit causality caveats")
    void synthesizeReport_CriticRejection_ProducesCaveats() {
        UUID invId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .title("Checkout API error rate spike")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .incidentId(incidentId)
                .status(InvestigationStatus.WAITING)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .build();

        CriticResult rejectedResult = CriticResult.builder()
                .decision(CriticDecision.REJECT)
                .reasons(List.of("Temporal proximity does not establish root cause"))
                .failedChecks(List.of("UNSUPPORTED_CAUSAL_CLAIM"))
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of(rejectedResult));
        when(blackboard.getMetrics()).thenReturn(InvestigationMetrics.builder().build());

        InvestigationReportResponse report = synthesizer.synthesizeReport(
                incident,
                investigation,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                blackboard
        );

        assertThat(report).isNotNull();
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.SUPPORTED_FINDING);
        assertThat(report.getFinalConclusion()).contains("repository change evidence alone does not establish causal proof");
        assertThat(report.getCriticSummary().getRejectedCount()).isEqualTo(1);
        assertThat(report.getUnresolvedQuestions()).isNotEmpty();
    }

    @Test
    @DisplayName("synthesizeReport: sensitive production action produces UNKNOWN with human approval warning")
    void synthesizeReport_HumanApprovalRequired_Alerts() {
        UUID invId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .title("Checkout API error rate spike")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .incidentId(incidentId)
                .status(InvestigationStatus.WAITING)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .build();

        CriticResult humanApprovalResult = CriticResult.builder()
                .decision(CriticDecision.HUMAN_APPROVAL_REQUIRED)
                .reasons(List.of("Automated production rollback proposed"))
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of(humanApprovalResult));
        when(blackboard.getMetrics()).thenReturn(InvestigationMetrics.builder().build());

        InvestigationReportResponse report = synthesizer.synthesizeReport(
                incident,
                investigation,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                blackboard
        );

        assertThat(report).isNotNull();
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.UNKNOWN);
        assertThat(report.getFinalConclusion()).contains("requires human engineer authorization");
        assertThat(report.getStopping().isHumanApprovalRequired()).isTrue();
    }

    @Test
    @DisplayName("synthesizeReport: exhausted recovery attempts produces UNRESOLVED conclusion")
    void synthesizeReport_ExhaustedRecovery_ProducesUnresolved() {
        UUID invId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .title("Checkout API error rate spike")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .incidentId(incidentId)
                .status(InvestigationStatus.WAITING)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .build();

        RecoveryAttempt exhaustedAttempt = RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(invId)
                .recoveryType(RecoveryType.RETRY)
                .status(RecoveryStatus.FAILED)
                .attemptNumber(3)
                .reason("HTTP 503 retry budget exhausted")
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of());
        when(blackboard.getMetrics()).thenReturn(InvestigationMetrics.builder().build());

        InvestigationReportResponse report = synthesizer.synthesizeReport(
                incident,
                investigation,
                List.of(),
                List.of(),
                List.of(exhaustedAttempt),
                List.of(),
                blackboard
        );

        assertThat(report).isNotNull();
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.UNRESOLVED);
        assertThat(report.getFinalConclusion()).contains("recovery attempts exhausted");
        assertThat(report.getRecoverySummary().getExhaustedRecoveries()).isEqualTo(1);
    }
}
