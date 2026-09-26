package com.incidentmind.investigation.service;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.repository.AuditEventRepository;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import com.incidentmind.investigation.dto.AuditEventResponse;
import com.incidentmind.investigation.dto.CreateInvestigationRequest;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.dto.TaskResponse;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.investigation.repository.InvestigationRepository;
import com.incidentmind.agent.core.AgentExecutionService;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.planner.core.InvestigationPlanner;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationServiceTest {

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
    private InvestigationPlanner investigationPlanner;

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private AuditService auditService;

    @Mock
    private com.incidentmind.recovery.repository.RecoveryAttemptRepository recoveryAttemptRepository;

    @InjectMocks
    private InvestigationServiceImpl investigationService;

    @Test
    @DisplayName("Create investigation succeeds when incident exists and records audit event")
    void createInvestigation_Success() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-000001")
                .title("Checkout degradation")
                .description("High latency")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .build();

        CreateInvestigationRequest request = CreateInvestigationRequest.builder()
                .objective("Find root cause of 5xx errors")
                .maxTasks(15)
                .maxRetriesPerTask(3)
                .maxRuntimeSeconds(180)
                .build();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvestigationResponse response = investigationService.createInvestigation(incidentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getIncidentId()).isEqualTo(incidentId);
        assertThat(response.getStatus()).isEqualTo(InvestigationStatus.CREATED);
        assertThat(response.getObjective()).isEqualTo("Find root cause of 5xx errors");
        assertThat(response.getMaxTasks()).isEqualTo(15);
        assertThat(response.getMaxRetriesPerTask()).isEqualTo(3);
        assertThat(response.getMaxRuntimeSeconds()).isEqualTo(180);

        verify(auditService).recordEvent(
                eq(response.getId()),
                isNull(),
                isNull(),
                eq(AuditEventType.INVESTIGATION_CREATED),
                eq(ActorType.USER),
                eq("api-user"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Create investigation throws ResourceNotFoundException when incident does not exist")
    void createInvestigation_IncidentNotFound() {
        UUID incidentId = UUID.randomUUID();
        CreateInvestigationRequest request = CreateInvestigationRequest.builder()
                .objective("Root cause")
                .maxTasks(5)
                .maxRetriesPerTask(1)
                .maxRuntimeSeconds(60)
                .build();

        when(incidentRepository.findById(incidentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investigationService.createInvestigation(incidentId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(incidentId.toString());
    }

    @Test
    @DisplayName("Get investigation tasks returns tasks ordered by created_at asc")
    void getInvestigationTasks_Success() {
        UUID investigationId = UUID.randomUUID();
        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskType("LOG_ANALYSIS")
                .title("Check error logs")
                .description("Query elasticsearch logs")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.READY)
                .attemptCount(0)
                .maxAttempts(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(task));

        List<TaskResponse> tasks = investigationService.getInvestigationTasks(investigationId);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskType()).isEqualTo("LOG_ANALYSIS");
        assertThat(tasks.get(0).getTitle()).isEqualTo("Check error logs");
    }

    @Test
    @DisplayName("Get investigation evidence returns evidence records")
    void getInvestigationEvidence_Success() {
        UUID investigationId = UUID.randomUUID();
        Evidence ev = Evidence.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .sourceType("DATABASE")
                .sourceReference("postgres:pg_stat_activity")
                .claim("Deadlock detected in checkout_orders table")
                .rawData(Map.of("lock_type", "relation", "relation", "checkout_orders"))
                .confidence(new BigDecimal("0.9950"))
                .collectedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(ev));

        List<EvidenceResponse> evidenceList = investigationService.getInvestigationEvidence(investigationId);

        assertThat(evidenceList).hasSize(1);
        assertThat(evidenceList.get(0).getSourceType()).isEqualTo("DATABASE");
        assertThat(evidenceList.get(0).getClaim()).isEqualTo("Deadlock detected in checkout_orders table");
    }

    @Test
    @DisplayName("Get investigation audit trail returns events in chronological order")
    void getInvestigationAuditTrail_Success() {
        UUID investigationId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-09-26T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-26T10:05:00Z");

        AuditEvent ev1 = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.INVESTIGATION_CREATED)
                .actorType(ActorType.USER)
                .correlationId(UUID.randomUUID())
                .occurredAt(t1)
                .build();

        AuditEvent ev2 = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.TASK_STARTED)
                .actorType(ActorType.ORCHESTRATOR)
                .correlationId(UUID.randomUUID())
                .occurredAt(t2)
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId)).thenReturn(List.of(ev1, ev2));

        List<AuditEventResponse> trail = investigationService.getInvestigationAuditTrail(investigationId);

        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).getOccurredAt()).isEqualTo(t1);
        assertThat(trail.get(1).getOccurredAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("startInvestigation: orchestrates planning, task generation, agent execution, and dynamic replanning")
    void startInvestigation_OrchestratesDynamicMultiAgentPlan() {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();

        Incident incident = Incident.builder()
                .id(incidentId)
                .incidentKey("INC-000001")
                .title("Checkout API error rate increased")
                .description("Spike after release")
                .serviceName("checkout-service")
                .build();

        Investigation investigation = Investigation.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .maxTasks(5)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .build();

        when(investigationRepository.findById(investigationId)).thenReturn(Optional.of(investigation));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
        when(investigationRepository.save(any(Investigation.class))).thenAnswer(inv -> inv.getArgument(0));

        // Planner returns 1 task on first call, then NO_FURTHER_TASKS
        com.incidentmind.planner.model.PlannedTask plannedTask = com.incidentmind.planner.model.PlannedTask.builder()
                .taskType("INCIDENT_TRIAGE")
                .title("Initial Triage")
                .priority(TaskPriority.HIGH)
                .build();

        com.incidentmind.planner.model.InvestigationPlan plan1 = com.incidentmind.planner.model.InvestigationPlan.builder()
                .tasks(List.of(plannedTask))
                .nextAction(com.incidentmind.planner.model.PlanAction.EXECUTE_TASKS)
                .build();

        com.incidentmind.planner.model.InvestigationPlan plan2 = com.incidentmind.planner.model.InvestigationPlan.empty("Investigation complete");

        when(investigationPlanner.plan(any()))
                .thenReturn(plan1)
                .thenReturn(plan2);

        when(taskRepository.save(any(InvestigationTask.class))).thenAnswer(inv -> {
            InvestigationTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        InvestigationResponse response = investigationService.startInvestigation(investigationId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(InvestigationStatus.WAITING);

        verify(auditService).recordEvent(
                eq(investigationId),
                isNull(),
                isNull(),
                eq(AuditEventType.INVESTIGATION_STARTED),
                eq(ActorType.ORCHESTRATOR),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("getInvestigationAgentRuns: returns list of agent runs for investigation")
    void getInvestigationAgentRuns_Success() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        InvestigationTask task = InvestigationTask.builder()
                .id(taskId)
                .investigationId(investigationId)
                .build();

        com.incidentmind.agent.entity.AgentRun run = com.incidentmind.agent.entity.AgentRun.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .agentType("incident-triage-agent")
                .status(com.incidentmind.agent.entity.AgentRunStatus.COMPLETED)
                .durationMs(100L)
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(task));
        when(agentRunRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(run));

        List<com.incidentmind.agent.dto.AgentRunResponse> runs = investigationService.getInvestigationAgentRuns(investigationId);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getAgentType()).isEqualTo("incident-triage-agent");
    }

    @Test
    @DisplayName("getInvestigationRecoveryAttempts: returns list of recovery attempts for investigation")
    void getInvestigationRecoveryAttempts_Success() {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        com.incidentmind.recovery.entity.RecoveryAttempt attempt = com.incidentmind.recovery.entity.RecoveryAttempt.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(taskId)
                .recoveryType(com.incidentmind.recovery.entity.RecoveryType.RETRY)
                .status(com.incidentmind.recovery.entity.RecoveryStatus.SUCCESS)
                .attemptNumber(1)
                .reason("Transient 503 error")
                .createdAt(Instant.now())
                .build();

        when(investigationRepository.existsById(investigationId)).thenReturn(true);
        when(recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId)).thenReturn(List.of(attempt));

        List<com.incidentmind.recovery.dto.RecoveryAttemptResponse> attempts = investigationService.getInvestigationRecoveryAttempts(investigationId);

        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getRecoveryType()).isEqualTo(com.incidentmind.recovery.entity.RecoveryType.RETRY);
        assertThat(attempts.get(0).getStatus()).isEqualTo(com.incidentmind.recovery.entity.RecoveryStatus.SUCCESS);
    }
}
