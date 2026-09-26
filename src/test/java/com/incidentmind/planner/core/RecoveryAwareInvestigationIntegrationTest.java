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
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.investigation.repository.InvestigationRepository;
import com.incidentmind.investigation.service.InvestigationServiceImpl;
import com.incidentmind.recovery.config.RecoveryProperties;
import com.incidentmind.recovery.core.DefaultRecoveryEngine;
import com.incidentmind.recovery.core.RecoveryEngine;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.recovery.policy.DefaultRecoveryPolicy;
import com.incidentmind.recovery.policy.ExponentialBackoffStrategy;
import com.incidentmind.recovery.policy.RecoveryPolicy;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoveryAwareInvestigationIntegrationTest {

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
        inMemoryTasks = new ArrayList<>();
        inMemoryEvidence = new ArrayList<>();
        inMemoryAgentRuns = new ArrayList<>();
        inMemoryRecoveryAttempts = new ArrayList<>();
        inMemoryAuditEvents = new ArrayList<>();

        // Configure Recovery Engine
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

        // Build agents
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

        DeterministicPlanner planner = new DeterministicPlanner();

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
                recoveryAttemptRepository
        );

        // Repository wiring
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
            inMemoryAgentRuns.removeIf(existing -> existing.getId().equals(r.getId()));
            inMemoryAgentRuns.add(r);
            return r;
        });

        when(recoveryAttemptRepository.save(any(RecoveryAttempt.class))).thenAnswer(inv -> {
            RecoveryAttempt ra = inv.getArgument(0);
            if (ra.getId() == null) ra.setId(UUID.randomUUID());
            inMemoryRecoveryAttempts.removeIf(existing -> existing.getId().equals(ra.getId()));
            inMemoryRecoveryAttempts.add(ra);
            return ra;
        });

        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> new ArrayList<>(inMemoryRecoveryAttempts));
    }

    @Test
    @DisplayName("End-to-end recovery: HTTP 503 transient failure on attempt 1 recovers on attempt 2, collects evidence, and continues investigation")
    void testInvestigation_Transient503Failure_RecoversAndContinues() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-RECOVERY-001")
                .title("Checkout API error rate increased from 2% to 18%")
                .description("High error rate following payment-service release")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .objective("Root cause analysis of checkout API degradation")
                .maxTasks(10)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(300)
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Tool Gateway behavior: 1st call fails with 503, 2nd call succeeds with 200
        AtomicInteger toolCallCount = new AtomicInteger(0);
        when(toolGateway.invokeTool(any(ToolRequest.class))).thenAnswer(inv -> {
            int call = toolCallCount.incrementAndGet();
            if (call == 1) {
                return ToolResult.failure(
                        "github.get_recent_commits",
                        "EXTERNAL_API",
                        ToolCallStatus.FAILED,
                        503,
                        "HTTP_503",
                        "Simulated service unavailable (transient failure)",
                        ErrorClassification.HTTP_5XX,
                        Map.of("error", "Service Unavailable"),
                        Map.of(),
                        80L
                );
            } else {
                Map<String, Object> commitData = Map.of(
                        "sha", "c0ffee1234567890",
                        "commit", Map.of("message", "Fix checkout validation timeout")
                );
                return ToolResult.success(
                        "github.get_recent_commits",
                        "EXTERNAL_API",
                        200,
                        Map.of("items", List.of(commitData), "count", 1),
                        Map.of(),
                        120L
                );
            }
        });

        // Run full dynamic investigation
        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(InvestigationStatus.WAITING);

        // Verify recovery attempts were recorded
        assertThat(inMemoryRecoveryAttempts).isNotEmpty();
        RecoveryAttempt recoveryAttempt = inMemoryRecoveryAttempts.get(0);
        assertThat(recoveryAttempt.getRecoveryType()).isEqualTo(RecoveryType.RETRY);
        assertThat(recoveryAttempt.getStatus()).isEqualTo(RecoveryStatus.SUCCESS);

        // Verify evidence was collected following successful retry
        assertThat(inMemoryEvidence).hasSize(1);
        Evidence evidence = inMemoryEvidence.get(0);
        assertThat(evidence.getSourceType()).isEqualTo("GITHUB");
        assertThat(evidence.getClaim()).contains("c0ffee1");

        // Verify that all 3 tasks were created and completed dynamically: Triage -> Change -> Dependency
        assertThat(inMemoryTasks).hasSize(3);
        assertThat(inMemoryTasks.get(0).getTaskType()).isEqualTo("INCIDENT_TRIAGE");
        assertThat(inMemoryTasks.get(0).getStatus()).isEqualTo(TaskStatus.COMPLETED);

        assertThat(inMemoryTasks.get(1).getTaskType()).isEqualTo("CHANGE_ANALYSIS");
        assertThat(inMemoryTasks.get(1).getStatus()).isEqualTo(TaskStatus.COMPLETED);

        assertThat(inMemoryTasks.get(2).getTaskType()).isEqualTo("DEPENDENCY_ANALYSIS");
        assertThat(inMemoryTasks.get(2).getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("End-to-end recovery: Persistent failure exhausts retries, triggers REPLAN, and Planner proceeds with alternative task")
    void testInvestigation_PersistentFailure_ExhaustsRetriesAndReplans() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-RECOVERY-002")
                .title("Checkout API error rate increased")
                .description("Checkout API outage")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .objective("Root cause analysis")
                .maxTasks(10)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(300)
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Tool Gateway behavior: Always fails with 500
        when(toolGateway.invokeTool(any(ToolRequest.class))).thenAnswer(inv -> ToolResult.failure(
                "github.get_recent_commits",
                "EXTERNAL_API",
                ToolCallStatus.FAILED,
                500,
                "HTTP_500",
                "Persistent internal server error",
                ErrorClassification.HTTP_5XX,
                Map.of("error", "Internal Server Error"),
                Map.of(),
                50L
        ));

        // Start investigation
        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(InvestigationStatus.WAITING);

        // Verify recovery attempts occurred
        assertThat(inMemoryRecoveryAttempts).isNotEmpty();

        // Verify change analysis task was marked FAILED
        Optional<InvestigationTask> changeTask = inMemoryTasks.stream()
                .filter(t -> "CHANGE_ANALYSIS".equalsIgnoreCase(t.getTaskType()))
                .findFirst();
        assertThat(changeTask).isPresent();
        assertThat(changeTask.get().getStatus()).isEqualTo(TaskStatus.FAILED);

        // Verify Planner dynamically created alternative DEPENDENCY_ANALYSIS task despite Change Analysis failure!
        Optional<InvestigationTask> depTask = inMemoryTasks.stream()
                .filter(t -> "DEPENDENCY_ANALYSIS".equalsIgnoreCase(t.getTaskType()))
                .findFirst();
        assertThat(depTask).isPresent();
        assertThat(depTask.get().getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // No fake evidence created for failed GitHub call
        assertThat(inMemoryEvidence).isEmpty();
    }
}
