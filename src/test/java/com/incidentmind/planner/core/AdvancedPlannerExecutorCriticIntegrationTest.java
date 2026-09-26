package com.incidentmind.planner.core;

import com.incidentmind.agent.core.AgentRegistry;
import com.incidentmind.agent.core.DefaultAgentExecutionService;
import com.incidentmind.agent.core.DefaultAgentRegistry;
import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.agent.specialized.ChangeAnalysisAgent;
import com.incidentmind.agent.specialized.DependencyAnalysisAgent;
import com.incidentmind.agent.specialized.IncidentTriageAgent;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.repository.AuditEventRepository;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.blackboard.dto.InvestigationMetricsResponse;
import com.incidentmind.critic.core.DefaultInvestigationCritic;
import com.incidentmind.critic.core.InvestigationCritic;
import com.incidentmind.critic.dto.CriticEvaluationResponse;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.executor.core.DefaultInvestigationExecutor;
import com.incidentmind.executor.core.InvestigationExecutor;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.repository.InvestigationRepository;
import com.incidentmind.investigation.service.InvestigationServiceImpl;
import com.incidentmind.recovery.config.RecoveryProperties;
import com.incidentmind.recovery.core.DefaultRecoveryEngine;
import com.incidentmind.recovery.core.RecoveryEngine;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.policy.DefaultRecoveryPolicy;
import com.incidentmind.recovery.policy.ExponentialBackoffStrategy;
import com.incidentmind.recovery.policy.RecoveryPolicy;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.stopping.core.DefaultInvestigationStoppingPolicy;
import com.incidentmind.stopping.core.InvestigationStoppingPolicy;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdvancedPlannerExecutorCriticIntegrationTest {

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
    private AuditService auditService;
    @Mock
    private RecoveryAttemptRepository recoveryAttemptRepository;
    @Mock
    private ToolGateway toolGateway;

    private List<InvestigationTask> inMemoryTasks;
    private List<Evidence> inMemoryEvidence;
    private List<AgentRun> inMemoryAgentRuns;
    private List<RecoveryAttempt> inMemoryRecoveryAttempts;
    private List<AuditEvent> inMemoryAuditEvents;

    private InvestigationServiceImpl investigationService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(toolGateway, auditService, taskRepository, evidenceRepository,
                agentRunRepository, recoveryAttemptRepository, incidentRepository, investigationRepository);

        inMemoryTasks = new ArrayList<>();
        inMemoryEvidence = new ArrayList<>();
        inMemoryAgentRuns = new ArrayList<>();
        inMemoryRecoveryAttempts = new ArrayList<>();
        inMemoryAuditEvents = new ArrayList<>();

        // 1. Configure Recovery
        RecoveryProperties recoveryProps = new RecoveryProperties();
        recoveryProps.setEnabled(true);
        recoveryProps.setMaxAttempts(3);
        recoveryProps.setInitialBackoffMs(10);
        recoveryProps.setMaxBackoffMs(50);
        recoveryProps.setExponentialBackoff(true);

        ExponentialBackoffStrategy backoffStrategy = new ExponentialBackoffStrategy(recoveryProps, millis -> {});
        RecoveryPolicy recoveryPolicy = new DefaultRecoveryPolicy(recoveryProps, backoffStrategy);
        RecoveryEngine recoveryEngine = new DefaultRecoveryEngine(
                recoveryPolicy,
                recoveryAttemptRepository,
                auditService,
                toolGateway,
                backoffStrategy
        );

        // 2. Agents
        IncidentTriageAgent triageAgent = new IncidentTriageAgent();
        ChangeAnalysisAgent changeAgent = new ChangeAnalysisAgent(recoveryEngine);
        DependencyAnalysisAgent dependencyAgent = new DependencyAnalysisAgent();

        AgentRegistry agentRegistry = new DefaultAgentRegistry(List.of(triageAgent, changeAgent, dependencyAgent));

        DefaultAgentExecutionService agentExecutionService = new DefaultAgentExecutionService(
                agentRegistry,
                agentRunRepository,
                taskRepository,
                evidenceRepository,
                auditService,
                toolGateway,
                recoveryEngine
        );

        // 3. Critic, Stopping Policy, Planner, Executor
        InvestigationCritic critic = new DefaultInvestigationCritic(auditService);
        InvestigationStoppingPolicy stoppingPolicy = new DefaultInvestigationStoppingPolicy(auditService);
        DeterministicPlanner planner = new DeterministicPlanner();
        InvestigationExecutor executor = new DefaultInvestigationExecutor(
                agentExecutionService,
                critic,
                auditService,
                taskRepository
        );

        investigationService = new InvestigationServiceImpl(
                investigationRepository,
                incidentRepository,
                taskRepository,
                evidenceRepository,
                auditEventRepository,
                agentRunRepository,
                auditService,
                planner,
                agentExecutionService,
                recoveryAttemptRepository,
                executor,
                stoppingPolicy
        );

        // 4. In-Memory Wiring
        when(taskRepository.save(any(InvestigationTask.class))).thenAnswer(inv -> {
            InvestigationTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            inMemoryTasks.removeIf(existing -> existing.getId().equals(t.getId()));
            inMemoryTasks.add(t);
            return t;
        });

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> new ArrayList<>(inMemoryTasks));

        when(taskRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return inMemoryTasks.stream().filter(t -> t.getId().equals(id)).findFirst();
        });

        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            inMemoryEvidence.add(e);
            return e;
        });

        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> new ArrayList<>(inMemoryEvidence));

        when(agentRunRepository.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            inMemoryAgentRuns.add(r);
            return r;
        });

        when(agentRunRepository.findByTaskIdOrderByCreatedAtAsc(any(UUID.class))).thenAnswer(inv -> {
            UUID taskId = inv.getArgument(0);
            return inMemoryAgentRuns.stream().filter(r -> r.getTaskId().equals(taskId)).collect(Collectors.toList());
        });

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> {
            RecoveryAttempt ra = inv.getArgument(0);
            if (ra.getId() == null) ra.setId(UUID.randomUUID());
            inMemoryRecoveryAttempts.add(ra);
            return ra;
        });

        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> new ArrayList<>(inMemoryRecoveryAttempts));

        when(auditService.recordEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    AuditEvent evt = AuditEvent.builder()
                            .id(UUID.randomUUID())
                            .investigationId(inv.getArgument(0))
                            .taskId(inv.getArgument(1))
                            .eventType(inv.getArgument(3))
                            .actorType(inv.getArgument(4))
                            .actorId(inv.getArgument(5))
                            .eventData(inv.getArgument(6))
                            .correlationId(inv.getArgument(7))
                            .occurredAt(Instant.now())
                            .build();
                    inMemoryAuditEvents.add(evt);
                    return evt;
                });

        when(toolGateway.invokeTool(any(ToolRequest.class))).thenAnswer(inv -> {
            return ToolResult.builder()
                    .success(true)
                    .toolName("github.get_recent_commits")
                    .toolCallId(UUID.randomUUID())
                    .status(ToolCallStatus.SUCCESS)
                    .responsePayload(Map.of(
                            "commits", List.of(
                                    Map.of(
                                            "sha", "abc1234567890",
                                            "commit", Map.of(
                                                    "message", "feat: update payment validation pipeline",
                                                    "author", Map.of("name", "engineer1", "date", "2026-03-31T09:45:00Z")
                                            )
                                    )
                            )
                    ))
                    .durationMs(45L)
                    .build();
        });
    }

    @Test
    @DisplayName("End-to-End: Planner -> Executor -> Critic Acceptance -> Dynamic Multi-Task Workflow")
    void endToEnd_PlannerExecutorCritic_DynamicFlow() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-1001")
                .title("Checkout API error rate increased from 2% to 18%")
                .description("Checkout API error rate jumped after deployment of payment-service v2.4.0 in production repository:octocat/Hello-World.")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .objective("Identify root cause of checkout API degradation")
                .maxTasks(6)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .startedAt(Instant.now())
                .build();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Run Investigation
        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response).isNotNull();
        assertThat(inMemoryTasks).isNotEmpty();

        // 1. Verify Triage Task was planned and executed
        InvestigationTask triageTask = inMemoryTasks.stream()
                .filter(t -> "INCIDENT_TRIAGE".equalsIgnoreCase(t.getTaskType()))
                .findFirst()
                .orElse(null);
        assertThat(triageTask).isNotNull();
        assertThat(triageTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // 2. Verify Change Analysis Task was planned and executed
        InvestigationTask changeTask = inMemoryTasks.stream()
                .filter(t -> "CHANGE_ANALYSIS".equalsIgnoreCase(t.getTaskType()))
                .findFirst()
                .orElse(null);
        assertThat(changeTask).isNotNull();
        assertThat(changeTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // 3. Verify Blackboard Metrics
        InvestigationMetricsResponse metrics = investigationService.getInvestigationMetrics(investigationId);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTasksCreatedCount()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.getTasksCompletedCount()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.getCriticEvaluationsCount()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.getCriticAcceptancesCount()).isGreaterThanOrEqualTo(2);

        // 4. Verify Critic Decisions recorded
        List<CriticEvaluationResponse> criticEvals = investigationService.getInvestigationCriticEvaluations(investigationId);
        assertThat(criticEvals).isNotEmpty();
        assertThat(criticEvals.stream().anyMatch(e -> e.getDecision() == com.incidentmind.critic.model.CriticDecision.ACCEPT)).isTrue();

        // 5. Verify Audit Trail completeness
        assertThat(inMemoryAuditEvents.stream().anyMatch(e -> e.getEventType() == AuditEventType.PLAN_CREATED)).isTrue();
        assertThat(inMemoryAuditEvents.stream().anyMatch(e -> e.getEventType() == AuditEventType.EXECUTOR_STARTED)).isTrue();
        assertThat(inMemoryAuditEvents.stream().anyMatch(e -> e.getEventType() == AuditEventType.CRITIC_ACCEPTED)).isTrue();
    }

    @Test
    @DisplayName("End-to-End: Overconfident causal claim -> Critic REJECT -> Planner dynamically creates INVESTIGATE_PULL_REQUESTS")
    void endToEnd_CriticRejection_DynamicReplanning_Success() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-1002")
                .title("Checkout API error rate increased from 2% to 18%")
                .description("Checkout API error rate jumped after deployment of payment-service v2.4.0 in production repository:octocat/Hello-World.")
                .serviceName("checkout-service")
                .environment("production")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .objective("Identify root cause of checkout API degradation")
                .maxTasks(6)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .startedAt(Instant.now())
                .build();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock ToolGateway to return commit history containing unsupported causal claim
        when(toolGateway.invokeTool(any(ToolRequest.class))).thenAnswer(inv -> {
            return ToolResult.builder()
                    .success(true)
                    .toolName("github.get_recent_commits")
                    .toolCallId(UUID.randomUUID())
                    .status(ToolCallStatus.SUCCESS)
                    .responsePayload(Map.of(
                            "commits", List.of(
                                    Map.of(
                                            "sha", "abc1234567890",
                                            "commit", Map.of(
                                                    "message", "Commit abc1234567890 caused the incident and is the single root cause",
                                                    "author", Map.of("name", "engineer1", "date", "2026-03-31T09:45:00Z")
                                            )
                                    )
                            )
                    ))
                    .durationMs(45L)
                    .build();
        });

        // Run Investigation
        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response).isNotNull();

        // 1. Verify Critic REJECT was recorded
        List<CriticEvaluationResponse> criticEvals = investigationService.getInvestigationCriticEvaluations(investigationId);
        assertThat(criticEvals).isNotEmpty();
        boolean hasRejection = criticEvals.stream().anyMatch(e -> e.getDecision() == com.incidentmind.critic.model.CriticDecision.REJECT);
        assertThat(hasRejection).isTrue();

        // 2. Verify Planner dynamically created INVESTIGATE_PULL_REQUESTS in response to rejection
        boolean prTaskPlanned = inMemoryTasks.stream().anyMatch(t -> "INVESTIGATE_PULL_REQUESTS".equalsIgnoreCase(t.getTaskType()));
        assertThat(prTaskPlanned).isTrue();

        // 3. Verify Blackboard tracked rejections
        InvestigationMetricsResponse metrics = investigationService.getInvestigationMetrics(investigationId);
        assertThat(metrics.getCriticRejectionsCount()).isGreaterThanOrEqualTo(1);

        // 4. Verify Audit trail has CRITIC_REJECTED event
        assertThat(inMemoryAuditEvents.stream().anyMatch(e -> e.getEventType() == AuditEventType.CRITIC_REJECTED)).isTrue();
    }
}
