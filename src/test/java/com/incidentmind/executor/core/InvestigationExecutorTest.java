package com.incidentmind.executor.core;

import com.incidentmind.agent.core.AgentExecutionService;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.critic.core.InvestigationCritic;
import com.incidentmind.critic.model.CriticContext;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.executor.model.ExecutionContext;
import com.incidentmind.executor.model.ExecutionResult;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationExecutorTest {

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private InvestigationCritic critic;

    @Mock
    private AuditService auditService;

    @Mock
    private InvestigationTaskRepository taskRepository;

    @Mock
    private InvestigationBlackboard blackboard;

    private DefaultInvestigationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultInvestigationExecutor(agentExecutionService, critic, auditService, taskRepository);
    }

    @Test
    @DisplayName("execute: blocks task if parent task is not completed or failed")
    void execute_UnfinishedParent_BlocksTask() {
        UUID parentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();

        InvestigationTask parentTask = InvestigationTask.builder()
                .id(parentId)
                .status(TaskStatus.RUNNING)
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(taskId)
                .investigationId(invId)
                .parentTaskId(parentId)
                .status(TaskStatus.PENDING)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .task(task)
                .investigation(Investigation.builder().id(invId).build())
                .build();

        when(taskRepository.findById(parentId)).thenReturn(Optional.of(parentTask));

        ExecutionResult result = executor.execute(context);

        assertThat(result.isBlocked()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.BLOCKED);
        verify(taskRepository).save(task);
        verify(agentExecutionService, never()).executeTask(any(), any(), any(), any());
    }

    @Test
    @DisplayName("execute: agent output accepted by critic results in accepted ExecutionResult")
    void execute_CriticAccepts_ReturnsAccepted() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder().id(invId).build();
        Incident incident = Incident.builder().id(UUID.randomUUID()).build();
        InvestigationTask task = InvestigationTask.builder()
                .id(taskId)
                .investigationId(invId)
                .taskType("INCIDENT_TRIAGE")
                .status(TaskStatus.COMPLETED)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .incident(incident)
                .investigation(investigation)
                .task(task)
                .blackboard(blackboard)
                .correlationId(correlationId)
                .build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("summary", "Triage finished"),
                List.of(),
                List.of("DEPLOYMENT_REGRESSION"),
                List.of("CHANGE_ANALYSIS"),
                100L
        );

        CriticResult criticResult = CriticResult.builder()
                .decision(CriticDecision.ACCEPT)
                .reasons(List.of("Findings grounded in incident payload"))
                .build();

        when(agentExecutionService.executeTask(incident, investigation, task, correlationId))
                .thenReturn(agentResult);
        when(critic.evaluate(any(CriticContext.class)))
                .thenReturn(criticResult);

        ExecutionResult result = executor.execute(context);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getCriticResult().getDecision()).isEqualTo(CriticDecision.ACCEPT);

        verify(auditService).recordEvent(
                eq(invId),
                eq(taskId),
                any(),
                eq(AuditEventType.EXECUTOR_STARTED),
                eq(ActorType.ORCHESTRATOR),
                eq("investigation-executor"),
                any(),
                eq(correlationId)
        );
        verify(blackboard).recordCriticResult(criticResult);
    }

    @Test
    @DisplayName("execute: agent output rejected by critic marks task failed and returns rejected result")
    void execute_CriticRejects_ReturnsRejectedAndMarksFailed() {
        UUID taskId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder().id(invId).build();
        Incident incident = Incident.builder().id(UUID.randomUUID()).build();
        InvestigationTask task = InvestigationTask.builder()
                .id(taskId)
                .investigationId(invId)
                .taskType("CHANGE_ANALYSIS")
                .status(TaskStatus.COMPLETED)
                .build();

        ExecutionContext context = ExecutionContext.builder()
                .incident(incident)
                .investigation(investigation)
                .task(task)
                .blackboard(blackboard)
                .correlationId(correlationId)
                .build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("claim", "Commit caused incident"),
                List.of(),
                List.of(),
                List.of(),
                100L
        );

        CriticResult criticResult = CriticResult.builder()
                .decision(CriticDecision.REJECT)
                .reasons(List.of("Unsupported causal assertion"))
                .failedChecks(List.of("UNSUPPORTED_CAUSAL_CLAIM"))
                .recommendedNextAction("INVESTIGATE_PULL_REQUESTS")
                .build();

        when(agentExecutionService.executeTask(incident, investigation, task, correlationId))
                .thenReturn(agentResult);
        when(critic.evaluate(any(CriticContext.class)))
                .thenReturn(criticResult);

        ExecutionResult result = executor.execute(context);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.isAccepted()).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        verify(taskRepository).save(task);
    }
}
