package com.incidentmind.agent.core;

import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.core.ToolGateway;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentExecutionServiceTest {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private InvestigationTaskRepository taskRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ToolGateway toolGateway;

    @Mock
    private Agent mockAgent;

    private DefaultAgentExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new DefaultAgentExecutionService(
                agentRegistry,
                agentRunRepository,
                taskRepository,
                evidenceRepository,
                auditService,
                toolGateway
        );
    }

    @Test
    @DisplayName("AgentExecutionService: successful execution persists AgentRun, Evidence, Task state, and emits Audit events")
    void executeTask_Success() {
        Incident incident = Incident.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).build();
        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .investigationId(investigation.getId())
                .taskType("CHANGE_ANALYSIS")
                .title("Check GitHub changes")
                .status(TaskStatus.PENDING)
                .build();

        UUID correlationId = UUID.randomUUID();

        when(agentRegistry.getAgentForTask(task)).thenReturn(mockAgent);
        when(mockAgent.getName()).thenReturn("change-analysis-agent");

        when(agentRunRepository.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun ar = inv.getArgument(0);
            if (ar.getId() == null) ar.setId(UUID.randomUUID());
            return ar;
        });

        EvidenceDraft evidenceDraft = EvidenceDraft.builder()
                .sourceType("GITHUB")
                .sourceReference("commit:12345")
                .claim("Recent commit exists")
                .confidence(new BigDecimal("0.9000"))
                .rawData(Map.of("sha", "12345"))
                .build();

        AgentExecutionResult agentResult = AgentExecutionResult.success(
                Map.of("checked", true),
                List.of(evidenceDraft),
                List.of("Hypothesis A"),
                List.of(),
                150L
        );
        when(mockAgent.execute(any(AgentExecutionContext.class))).thenReturn(agentResult);

        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence ev = inv.getArgument(0);
            if (ev.getId() == null) ev.setId(UUID.randomUUID());
            return ev;
        });

        AgentExecutionResult result = executionService.executeTask(incident, investigation, task, correlationId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // Verify Evidence was saved
        verify(evidenceRepository).save(any(Evidence.class));

        // Verify Audit events recorded
        verify(auditService).recordEvent(
                eq(investigation.getId()),
                eq(task.getId()),
                any(),
                eq(AuditEventType.TASK_STARTED),
                eq(ActorType.AGENT),
                eq("change-analysis-agent"),
                any(),
                eq(correlationId)
        );

        verify(auditService).recordEvent(
                eq(investigation.getId()),
                eq(task.getId()),
                any(),
                eq(AuditEventType.EVIDENCE_CREATED),
                eq(ActorType.AGENT),
                eq("change-analysis-agent"),
                any(),
                eq(correlationId)
        );

        verify(auditService).recordEvent(
                eq(investigation.getId()),
                eq(task.getId()),
                any(),
                eq(AuditEventType.TASK_COMPLETED),
                eq(ActorType.AGENT),
                eq("change-analysis-agent"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("AgentExecutionService: failed execution marks AgentRun and Task as FAILED without throwing uncaught exceptions")
    void executeTask_Failure() {
        Incident incident = Incident.builder().id(UUID.randomUUID()).build();
        Investigation investigation = Investigation.builder().id(UUID.randomUUID()).build();
        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .investigationId(investigation.getId())
                .taskType("CHANGE_ANALYSIS")
                .status(TaskStatus.PENDING)
                .build();

        UUID correlationId = UUID.randomUUID();

        when(agentRegistry.getAgentForTask(task)).thenReturn(mockAgent);
        when(mockAgent.getName()).thenReturn("change-analysis-agent");
        when(agentRunRepository.save(any(AgentRun.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentExecutionResult failedResult = AgentExecutionResult.failure(
                AgentRunStatus.FAILED,
                "API_ERROR",
                "Connection timeout",
                Map.of(),
                100L
        );
        when(mockAgent.execute(any(AgentExecutionContext.class))).thenReturn(failedResult);

        AgentExecutionResult result = executionService.executeTask(incident, investigation, task, correlationId);

        assertThat(result.isSuccess()).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);

        verify(auditService).recordEvent(
                eq(investigation.getId()),
                eq(task.getId()),
                any(),
                eq(AuditEventType.TASK_FAILED),
                eq(ActorType.AGENT),
                eq("change-analysis-agent"),
                any(),
                eq(correlationId)
        );
    }
}
