package com.incidentmind.stopping.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.stopping.model.StoppingContext;
import com.incidentmind.stopping.model.StoppingDecision;
import com.incidentmind.stopping.model.StoppingResult;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class InvestigationStoppingPolicyTest {

    @Mock
    private AuditService auditService;

    @Mock
    private InvestigationBlackboard blackboard;

    private DefaultInvestigationStoppingPolicy stoppingPolicy;

    @BeforeEach
    void setUp() {
        stoppingPolicy = new DefaultInvestigationStoppingPolicy(auditService);
    }

    @Test
    @DisplayName("evaluate: stops with STOP_LIMIT_REACHED when max tasks limit exceeded")
    void evaluate_MaxTasksReached_Stops() {
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .maxTasks(2)
                .maxRuntimeSeconds(60)
                .startedAt(Instant.now().minusSeconds(10))
                .build();

        InvestigationTask t1 = InvestigationTask.builder().id(UUID.randomUUID()).status(TaskStatus.COMPLETED).build();
        InvestigationTask t2 = InvestigationTask.builder().id(UUID.randomUUID()).status(TaskStatus.COMPLETED).build();

        StoppingContext context = StoppingContext.builder()
                .investigation(investigation)
                .blackboard(blackboard)
                .tasks(List.of(t1, t2))
                .runtimeSeconds(10L)
                .correlationId(correlationId)
                .build();

        StoppingResult result = stoppingPolicy.evaluate(context);

        assertThat(result.isShouldStop()).isTrue();
        assertThat(result.getDecision()).isEqualTo(StoppingDecision.STOP_LIMIT_REACHED);

        verify(auditService).recordEvent(
                eq(invId),
                any(),
                any(),
                eq(AuditEventType.STOPPING_CHECK),
                eq(ActorType.ORCHESTRATOR),
                eq("stopping-policy"),
                any(),
                eq(correlationId)
        );
    }

    @Test
    @DisplayName("evaluate: stops with STOP_TIMEOUT when max runtime seconds exceeded")
    void evaluate_Timeout_Stops() {
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .maxTasks(10)
                .maxRuntimeSeconds(30)
                .startedAt(Instant.now().minusSeconds(40))
                .build();

        StoppingContext context = StoppingContext.builder()
                .investigation(investigation)
                .blackboard(blackboard)
                .tasks(List.of())
                .runtimeSeconds(40L)
                .correlationId(correlationId)
                .build();

        StoppingResult result = stoppingPolicy.evaluate(context);

        assertThat(result.isShouldStop()).isTrue();
        assertThat(result.getDecision()).isEqualTo(StoppingDecision.STOP_TIMEOUT);
    }

    @Test
    @DisplayName("evaluate: stops with HUMAN_APPROVAL_REQUIRED when sensitive action detected")
    void evaluate_HumanApprovalRequired_Stops() {
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .startedAt(Instant.now().minusSeconds(5))
                .build();

        CriticResult humanApprovalResult = CriticResult.builder()
                .decision(CriticDecision.HUMAN_APPROVAL_REQUIRED)
                .reasons(List.of("Proposed irreversible rollback"))
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of(humanApprovalResult));

        StoppingContext context = StoppingContext.builder()
                .investigation(investigation)
                .blackboard(blackboard)
                .tasks(List.of())
                .runtimeSeconds(5L)
                .correlationId(correlationId)
                .build();

        StoppingResult result = stoppingPolicy.evaluate(context);

        assertThat(result.isShouldStop()).isTrue();
        assertThat(result.getDecision()).isEqualTo(StoppingDecision.HUMAN_APPROVAL_REQUIRED);
    }

    @Test
    @DisplayName("evaluate: stops with STOP_SUCCESS when objective satisfied and evidence accepted by critic")
    void evaluate_SuccessCondition_Stops() {
        UUID invId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Investigation investigation = Investigation.builder()
                .id(invId)
                .maxTasks(10)
                .maxRuntimeSeconds(120)
                .startedAt(Instant.now().minusSeconds(5))
                .build();

        InvestigationTask t1 = InvestigationTask.builder().id(UUID.randomUUID()).status(TaskStatus.COMPLETED).build();
        Evidence e1 = Evidence.builder().id(UUID.randomUUID()).build();

        CriticResult acceptedResult = CriticResult.builder()
                .decision(CriticDecision.ACCEPT)
                .build();

        when(blackboard.getCriticResults()).thenReturn(List.of(acceptedResult));

        StoppingContext context = StoppingContext.builder()
                .investigation(investigation)
                .blackboard(blackboard)
                .tasks(List.of(t1))
                .evidence(List.of(e1))
                .runtimeSeconds(5L)
                .planHasNoFurtherTasks(true)
                .correlationId(correlationId)
                .build();

        StoppingResult result = stoppingPolicy.evaluate(context);

        assertThat(result.isShouldStop()).isTrue();
        assertThat(result.getDecision()).isEqualTo(StoppingDecision.STOP_SUCCESS);
    }
}
