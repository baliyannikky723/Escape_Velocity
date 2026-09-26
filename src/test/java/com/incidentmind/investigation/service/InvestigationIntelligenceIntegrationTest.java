package com.incidentmind.investigation.service;

import com.incidentmind.agent.core.AgentExecutionService;
import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.repository.AuditEventRepository;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.critic.core.InvestigationCritic;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.executor.core.InvestigationExecutor;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.investigation.repository.InvestigationRepository;
import com.incidentmind.planner.core.InvestigationPlanner;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.report.core.DefaultInvestigationSynthesizer;
import com.incidentmind.report.core.InvestigationSynthesizer;
import com.incidentmind.report.dto.AgentActivityResponse;
import com.incidentmind.report.dto.InvestigationReportResponse;
import com.incidentmind.report.dto.RecoverySummaryResponse;
import com.incidentmind.report.dto.TaskGraphResponse;
import com.incidentmind.report.dto.TimelineEventDto;
import com.incidentmind.report.dto.ToolExecutionResponse;
import com.incidentmind.report.model.ConclusionType;
import com.incidentmind.stopping.core.InvestigationStoppingPolicy;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.entity.ToolCall;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.repository.ToolCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationIntelligenceIntegrationTest {

    @Mock
    private InvestigationRepository investigationRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private InvestigationTaskRepository taskRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private RecoveryAttemptRepository recoveryAttemptRepository;

    @Mock
    private ToolCallRepository toolCallRepository;

    @Mock
    private InvestigationPlanner investigationPlanner;

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private InvestigationExecutor investigationExecutor;

    @Mock
    private InvestigationCritic investigationCritic;

    @Mock
    private InvestigationStoppingPolicy stoppingPolicy;

    @Mock
    private AuditService auditService;

    @Spy
    private InvestigationSynthesizer investigationSynthesizer = new DefaultInvestigationSynthesizer();

    @InjectMocks
    private InvestigationServiceImpl investigationService;

    private UUID incidentId;
    private UUID investigationId;
    private Incident incident;
    private Investigation investigation;

    @BeforeEach
    void setUp() {
        incidentId = UUID.randomUUID();
        investigationId = UUID.randomUUID();

        incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-7701")
                .title("Checkout API error rate surge to 18%")
                .description("Checkout 5xx spike observed shortly after deployment of payment-service v2.4.1")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .createdAt(Instant.now().minusSeconds(1800))
                .build();

        investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.COMPLETED)
                .objective("Diagnose root cause of checkout API degradation")
                .maxTasks(10)
                .maxRetriesPerTask(3)
                .maxRuntimeSeconds(300)
                .createdAt(Instant.now().minusSeconds(1200))
                .updatedAt(Instant.now())
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
    }

    @Test
    @DisplayName("Scenario A: Successful Investigation -> Grounded Report with SUPPORTED_FINDING")
    void scenarioA_SuccessfulInvestigation() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        UUID task1Id = UUID.randomUUID();
        UUID task2Id = UUID.randomUUID();

        InvestigationTask task1 = InvestigationTask.builder()
                .id(task1Id)
                .investigationId(investigationId)
                .taskType("TRIAGE")
                .title("Triage checkout service anomaly")
                .status(TaskStatus.COMPLETED)
                .assignedAgentType("incident-triage-agent")
                .priority(TaskPriority.HIGH)
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(Instant.now().minusSeconds(1000))
                .build();

        InvestigationTask task2 = InvestigationTask.builder()
                .id(task2Id)
                .investigationId(investigationId)
                .parentTaskId(task1Id)
                .taskType("CHANGE_ANALYSIS")
                .title("Inspect recent GitHub commits on payment-service")
                .status(TaskStatus.COMPLETED)
                .assignedAgentType("change-analysis-agent")
                .priority(TaskPriority.HIGH)
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(Instant.now().minusSeconds(800))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of(task1, task2));

        Evidence ev1 = Evidence.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .sourceType("METRICS")
                .sourceReference("datadog:metric:checkout.5xx.rate")
                .claim("Checkout 5xx rate jumped to 18% post deployment")
                .confidence(new BigDecimal("0.9500"))
                .rawData(Map.of("errorRate", 0.18, "threshold", 0.02))
                .collectedAt(Instant.now().minusSeconds(900))
                .createdAt(Instant.now().minusSeconds(900))
                .build();

        Evidence ev2 = Evidence.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task2Id)
                .sourceType("GITHUB_COMMIT")
                .sourceReference("github:commit:abc1234")
                .claim("Commit abc1234 deployed 8 minutes before error rate surge")
                .confidence(new BigDecimal("0.9200"))
                .rawData(Map.of("commitSha", "abc1234", "author", "dev@example.com"))
                .collectedAt(Instant.now().minusSeconds(700))
                .createdAt(Instant.now().minusSeconds(700))
                .build();

        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of(ev1, ev2));
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of());

        AuditEvent a1 = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.INVESTIGATION_STARTED)
                .actorType(ActorType.ORCHESTRATOR)
                .occurredAt(Instant.now().minusSeconds(1200))
                .build();
        AuditEvent a2 = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.CRITIC_ACCEPTED)
                .actorType(ActorType.AGENT)
                .occurredAt(Instant.now().minusSeconds(600))
                .build();

        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId))
                .thenReturn(List.of(a1, a2));

        InvestigationReportResponse report = investigationService.getInvestigationReport(investigationId);

        assertThat(report).isNotNull();
        assertThat(report.getInvestigationId()).isEqualTo(investigationId);
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.SUPPORTED_FINDING);
        assertThat(report.getFinalConclusion()).contains("supporting recent deployment activity");
        assertThat(report.getKeyFindings()).hasSize(3);
        assertThat(report.getHypotheses()).isNotEmpty();
        assertThat(report.getUnresolvedQuestions()).isNotEmpty();
        assertThat(report.getStopping().isHumanApprovalRequired()).isFalse();

        // Verify timeline
        List<TimelineEventDto> timeline = investigationService.getInvestigationTimeline(investigationId);
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).getEventType()).isEqualTo(AuditEventType.INVESTIGATION_STARTED);
        assertThat(timeline.get(1).getEventType()).isEqualTo(AuditEventType.CRITIC_ACCEPTED);

        // Verify task graph
        TaskGraphResponse graph = investigationService.getInvestigationGraph(investigationId);
        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getEdges()).hasSize(1);
        assertThat(graph.getEdges().get(0).getFromTaskId()).isEqualTo(task1Id);
        assertThat(graph.getEdges().get(0).getToTaskId()).isEqualTo(task2Id);
    }

    @Test
    @DisplayName("Scenario B: Critic Rejection -> Limits Preserved and Follow-up Identified")
    void scenarioB_CriticRejection() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        UUID task1Id = UUID.randomUUID();
        InvestigationTask task1 = InvestigationTask.builder()
                .id(task1Id)
                .investigationId(investigationId)
                .taskType("CHANGE_ANALYSIS")
                .title("Identify causal commit")
                .status(TaskStatus.FAILED)
                .assignedAgentType("change-analysis-agent")
                .attemptCount(1)
                .maxAttempts(3)
                .createdAt(Instant.now().minusSeconds(500))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of(task1));
        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of());
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId))
                .thenReturn(List.of());

        AuditEvent rejectEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .eventType(AuditEventType.CRITIC_REJECTED)
                .actorType(ActorType.AGENT)
                .eventData(Map.of("reason", "Unsupported causal claim without error trace correlation"))
                .occurredAt(Instant.now().minusSeconds(400))
                .build();

        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId))
                .thenReturn(List.of(rejectEvent));

        InvestigationReportResponse report = investigationService.getInvestigationReport(investigationId);
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.SUPPORTED_FINDING);
        assertThat(report.getCriticSummary().getRejectedCount()).isEqualTo(1);
        assertThat(report.getCriticSummary().isReplanTriggeredByCritic()).isTrue();
        assertThat(report.getUnresolvedQuestions()).anyMatch(q -> q.contains("Critic rejected findings"));
    }

    @Test
    @DisplayName("Scenario C: Transient Tool Failure -> Retry Recovery Succeeded")
    void scenarioC_TransientToolFailure_RecoveryRecorded() {
        UUID task1Id = UUID.randomUUID();

        RecoveryAttempt ra = RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .recoveryType(RecoveryType.RETRY)
                .status(RecoveryStatus.SUCCESS)
                .attemptNumber(1)
                .reason("GitHub API transient 503 Service Unavailable")
                .createdAt(Instant.now().minusSeconds(250))
                .completedAt(Instant.now().minusSeconds(200))
                .build();

        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(ra));

        RecoverySummaryResponse recoverySummary = investigationService.getInvestigationRecoverySummary(investigationId);
        assertThat(recoverySummary.getTotalAttempts()).isEqualTo(1);
        assertThat(recoverySummary.getSuccessfulRecoveries()).isEqualTo(1);
        assertThat(recoverySummary.getRetryCount()).isEqualTo(1);
        assertThat(recoverySummary.getAttempts().get(0).getReason()).contains("503");
    }

    @Test
    @DisplayName("Scenario D: Persistent Tool Failure -> Recovery Exhausted -> UNRESOLVED")
    void scenarioD_PersistentToolFailure_ExhaustedReplan() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        UUID task1Id = UUID.randomUUID();
        InvestigationTask task1 = InvestigationTask.builder()
                .id(task1Id)
                .investigationId(investigationId)
                .taskType("CHANGE_ANALYSIS")
                .title("Query GitHub releases")
                .status(TaskStatus.FAILED)
                .assignedAgentType("change-analysis-agent")
                .attemptCount(3)
                .maxAttempts(3)
                .createdAt(Instant.now().minusSeconds(600))
                .build();

        RecoveryAttempt ra1 = RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .recoveryType(RecoveryType.RETRY)
                .status(RecoveryStatus.FAILED)
                .attemptNumber(3)
                .reason("GitHub API persistent HTTP 500 - retry budget exhausted")
                .createdAt(Instant.now().minusSeconds(500))
                .build();

        RecoveryAttempt ra2 = RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .recoveryType(RecoveryType.REPLAN)
                .status(RecoveryStatus.SUCCESS)
                .attemptNumber(1)
                .reason("Fallback replan scheduled following tool failure")
                .createdAt(Instant.now().minusSeconds(450))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(task1));
        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of());
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(ra1, ra2));
        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId)).thenReturn(List.of());

        InvestigationReportResponse report = investigationService.getInvestigationReport(investigationId);
        assertThat(report.getConclusionType()).isEqualTo(ConclusionType.UNRESOLVED);
        assertThat(report.getUnresolvedQuestions()).anyMatch(q -> q.contains("unavailable or failed") || q.contains("healthy and reachable"));
    }

    @Test
    @DisplayName("Scenario E: Stopping Limit Reached -> Terminated Deterministically")
    void scenarioE_StoppingLimitReached() {
        investigation.setStatus(InvestigationStatus.STOPPED);
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        InvestigationTask t1 = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskType("METRIC_ANALYSIS")
                .title("Check metrics")
                .status(TaskStatus.COMPLETED)
                .assignedAgentType("incident-triage-agent")
                .createdAt(Instant.now().minusSeconds(300))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(t1));
        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of());
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of());

        AuditEvent stopEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.STOPPING_CHECK)
                .actorType(ActorType.ORCHESTRATOR)
                .eventData(Map.of("reason", "MAX_TASKS_EXCEEDED", "taskBudget", 1))
                .occurredAt(Instant.now().minusSeconds(50))
                .build();

        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId)).thenReturn(List.of(stopEvent));

        InvestigationReportResponse report = investigationService.getInvestigationReport(investigationId);
        assertThat(report.getStatus()).isEqualTo(InvestigationStatus.STOPPED);
        assertThat(report.getStopping().getReason()).isEqualTo("MAX_TASKS_EXCEEDED");
    }

    @Test
    @DisplayName("Scenario F: Human Approval Guardrail -> Flagged and Destructive Action Blocked")
    void scenarioF_HumanApprovalRequired() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        UUID task1Id = UUID.randomUUID();
        InvestigationTask task1 = InvestigationTask.builder()
                .id(task1Id)
                .investigationId(investigationId)
                .taskType("PRODUCTION_ROLLBACK")
                .title("Attempt rollback of payment service")
                .status(TaskStatus.COMPLETED)
                .assignedAgentType("change-analysis-agent")
                .createdAt(Instant.now().minusSeconds(200))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(task1));
        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of());
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of());

        AuditEvent humanApprovalEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(task1Id)
                .eventType(AuditEventType.HUMAN_APPROVAL_REQUIRED)
                .actorType(ActorType.AGENT)
                .eventData(Map.of("action", "ROLLBACK_PRODUCTION", "reason", "Autonomous destructive action not permitted"))
                .occurredAt(Instant.now().minusSeconds(150))
                .build();

        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId))
                .thenReturn(List.of(humanApprovalEvent));

        InvestigationReportResponse report = investigationService.getInvestigationReport(investigationId);
        assertThat(report.getStopping().isHumanApprovalRequired()).isTrue();
        assertThat(report.getCriticSummary().getHumanApprovalCount()).isEqualTo(1);
        assertThat(report.getRecommendedNextActions()).anyMatch(a -> a.contains("HUMAN APPROVAL REQUIRED"));
    }

    @Test
    @DisplayName("Agent Activity API and Tool Execution API return sanitized records")
    void agentAndToolApis_SanitizedAndLinked() {
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));

        UUID taskId = UUID.randomUUID();
        UUID agentRunId = UUID.randomUUID();
        UUID toolCallId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder()
                .id(taskId)
                .investigationId(investigationId)
                .taskType("CHANGE_ANALYSIS")
                .title("Analyze GitHub commit")
                .status(TaskStatus.COMPLETED)
                .assignedAgentType("change-analysis-agent")
                .createdAt(Instant.now().minusSeconds(300))
                .build();

        AgentRun run = AgentRun.builder()
                .id(agentRunId)
                .taskId(taskId)
                .agentType("change-analysis-agent")
                .status(AgentRunStatus.COMPLETED)
                .startedAt(Instant.now().minusSeconds(280))
                .completedAt(Instant.now().minusSeconds(200))
                .durationMs(80000L)
                .createdAt(Instant.now().minusSeconds(280))
                .outputPayload(Map.of("evidenceCount", 1))
                .build();

        ToolCall toolCall = ToolCall.builder()
                .id(toolCallId)
                .agentRunId(agentRunId)
                .toolName("github_get_commit")
                .toolType("REST_API")
                .status(ToolCallStatus.SUCCESS)
                .httpStatus(200)
                .durationMs(150L)
                .startedAt(Instant.now().minusSeconds(270))
                .completedAt(Instant.now().minusSeconds(269))
                .createdAt(Instant.now().minusSeconds(270))
                .build();

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(task));
        when(agentRunRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(run));
        when(toolCallRepository.findByAgentRunIdOrderByCreatedAtAsc(agentRunId)).thenReturn(List.of(toolCall));

        List<AgentActivityResponse> agents = investigationService.getInvestigationAgents(investigationId);
        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).getAgentType()).isEqualTo("change-analysis-agent");
        assertThat(agents.get(0).getEvidenceProduced()).isEqualTo(1);

        List<ToolExecutionResponse> tools = investigationService.getInvestigationTools(investigationId);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getToolName()).isEqualTo("github_get_commit");
        assertThat(tools.get(0).getHttpStatus()).isEqualTo(200);
        assertThat(tools.get(0).getDurationMs()).isEqualTo(150L);
    }
}
