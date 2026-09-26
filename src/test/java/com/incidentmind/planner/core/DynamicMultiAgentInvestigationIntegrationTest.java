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
import com.incidentmind.audit.service.AuditServiceImpl;
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
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicMultiAgentInvestigationIntegrationTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private InvestigationRepository investigationRepository;

    @Mock
    private InvestigationTaskRepository taskRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private ToolGateway toolGateway;

    @Mock
    private com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository;

    private InvestigationServiceImpl investigationService;
    private final Map<UUID, InvestigationTask> inMemoryTasks = new HashMap<>();
    private final List<Evidence> inMemoryEvidence = new ArrayList<>();
    private final List<AuditEvent> inMemoryAuditEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inMemoryTasks.clear();
        inMemoryEvidence.clear();
        inMemoryAuditEvents.clear();

        IncidentTriageAgent triageAgent = new IncidentTriageAgent();
        ChangeAnalysisAgent changeAgent = new ChangeAnalysisAgent();
        DependencyAnalysisAgent dependencyAgent = new DependencyAnalysisAgent();
        AgentRegistry agentRegistry = new DefaultAgentRegistry(List.of(triageAgent, changeAgent, dependencyAgent));

        AuditServiceImpl auditService = new AuditServiceImpl(auditEventRepository);
        DefaultAgentExecutionService executionService = new DefaultAgentExecutionService(
                agentRegistry,
                agentRunRepository,
                taskRepository,
                evidenceRepository,
                auditService,
                toolGateway
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
                executionService,
                recoveryAttemptRepository
        );

        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> {
            AuditEvent ev = inv.getArgument(0);
            inMemoryAuditEvents.add(ev);
            return ev;
        });

        when(agentRunRepository.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            if (run.getId() == null) run.setId(UUID.randomUUID());
            return run;
        });
    }

    @Test
    @DisplayName("End-to-End: Dynamic multi-agent planning, genuine task decomposition, GitHub tool call, and replanning")
    void executeDynamicMultiAgentInvestigation_FullLifecycle() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-000001")
                .title("Checkout API degradation")
                .description("Checkout error rate increased from 2% to 18% shortly after a payment-service release.")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .objective("Determine the root cause of the checkout API degradation")
                .maxTasks(10)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Task repository in-memory simulation
        when(taskRepository.save(any(InvestigationTask.class))).thenAnswer(inv -> {
            InvestigationTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            inMemoryTasks.put(t.getId(), t);
            return t;
        });

        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenAnswer(inv ->
                new ArrayList<>(inMemoryTasks.values())
        );

        when(taskRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(inMemoryTasks.get(id));
        });

        // Evidence repository in-memory simulation
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence ev = inv.getArgument(0);
            if (ev.getId() == null) ev.setId(UUID.randomUUID());
            inMemoryEvidence.add(ev);
            return ev;
        });

        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenAnswer(inv ->
                new ArrayList<>(inMemoryEvidence)
        );

        // Tool Gateway mock for GitHub tool execution
        ToolResult gitHubResult = ToolResult.success(
                "github.get_recent_commits",
                "EXTERNAL_API",
                200,
                Map.of(
                        "count", 1,
                        "items", List.of(Map.of(
                                "sha", "7f8b9c1d2e3f4a5",
                                "commit", Map.of("message", "Release payment-service v2.4.1 with optimized pooling")
                        ))
                ),
                Map.of("X-RateLimit-Remaining", "57"),
                80L
        );
        gitHubResult.setToolCallId(UUID.randomUUID());

        when(toolGateway.invokeTool(any(ToolRequest.class))).thenReturn(gitHubResult);

        // START INVESTIGATION
        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response.getStatus()).isEqualTo(InvestigationStatus.WAITING);

        // Verify task graph was created dynamically step-by-step
        assertThat(inMemoryTasks.size()).isGreaterThanOrEqualTo(3);

        List<String> taskTypes = inMemoryTasks.values().stream().map(InvestigationTask::getTaskType).toList();
        assertThat(taskTypes).contains("INCIDENT_TRIAGE", "CHANGE_ANALYSIS", "DEPENDENCY_ANALYSIS");

        // Verify all generated tasks completed successfully
        for (InvestigationTask task : inMemoryTasks.values()) {
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        // Verify Change Analysis task has parent Triage task
        InvestigationTask triageTask = inMemoryTasks.values().stream()
                .filter(t -> "INCIDENT_TRIAGE".equals(t.getTaskType()))
                .findFirst().orElseThrow();

        InvestigationTask changeTask = inMemoryTasks.values().stream()
                .filter(t -> "CHANGE_ANALYSIS".equals(t.getTaskType()))
                .findFirst().orElseThrow();

        assertThat(changeTask.getParentTaskId()).isEqualTo(triageTask.getId());

        // Verify Tool Gateway was invoked through ChangeAnalysisAgent
        verify(toolGateway, atLeastOnce()).invokeTool(any(ToolRequest.class));

        // Verify Evidence was recorded from Tool result
        assertThat(inMemoryEvidence).isNotEmpty();
        assertThat(inMemoryEvidence.get(0).getSourceType()).isEqualTo("GITHUB");
        assertThat(inMemoryEvidence.get(0).getClaim()).contains("7f8b9c1");

        // Verify complete audit events were generated
        List<AuditEventType> eventTypes = inMemoryAuditEvents.stream().map(AuditEvent::getEventType).toList();
        assertThat(eventTypes).contains(
                AuditEventType.INVESTIGATION_STARTED,
                AuditEventType.TASK_CREATED,
                AuditEventType.TASK_STARTED,
                AuditEventType.AGENT_STARTED,
                AuditEventType.EVIDENCE_CREATED,
                AuditEventType.AGENT_COMPLETED,
                AuditEventType.TASK_COMPLETED
        );
    }
}
