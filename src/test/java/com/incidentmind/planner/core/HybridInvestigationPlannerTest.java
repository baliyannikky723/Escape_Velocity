package com.incidentmind.planner.core;

import com.incidentmind.ai.client.AiIntelligenceClient;
import com.incidentmind.ai.dto.AiPlanRequest;
import com.incidentmind.ai.dto.AiPlanResponse;
import com.incidentmind.ai.dto.AiPlannedActionDto;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.planner.model.InvestigationPlan;
import com.incidentmind.planner.model.PlanAction;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridInvestigationPlannerTest {

    @Mock
    private AiIntelligenceClient aiClient;

    @Mock
    private AuditService auditService;

    private DeterministicPlanner deterministicPlanner;
    private HybridInvestigationPlanner hybridPlanner;

    private Incident incident;
    private Investigation investigation;

    @BeforeEach
    void setUp() {
        deterministicPlanner = new DeterministicPlanner();
        com.incidentmind.ai.config.AiServiceProperties props = new com.incidentmind.ai.config.AiServiceProperties();
        props.setEnabled(true);
        hybridPlanner = new HybridInvestigationPlanner(deterministicPlanner, aiClient, props, auditService);

        incident = Incident.builder()
                .id(UUID.randomUUID())
                .incidentKey("INC-000002")
                .title("Payment latency spike")
                .description("Payment gateway p99 latency spiked past 1500ms")
                .serviceName("payment-service")
                .build();

        investigation = Investigation.builder()
                .id(UUID.randomUUID())
                .incidentId(incident.getId())
                .maxTasks(10)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .build();
    }

    @Test
    @DisplayName("HybridPlanner: delegates to AI Client and parses structured plan successfully")
    void plan_AiSuccess_ProducesStructuredPlan() {
        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of())
                .evidence(List.of())
                .build();

        AiPlanResponse aiResponse = AiPlanResponse.builder()
                .investigationId(investigation.getId())
                .decision("CONTINUE")
                .reason("Classify incident severity and affected endpoints.")
                .actions(List.of(
                        AiPlannedActionDto.builder()
                                .action("CREATE_TASK")
                                .taskType("INCIDENT_TRIAGE")
                                .agentType("incident-triage-agent")
                                .priority("HIGH")
                                .toolRequirement("NO_TOOL_REQUIRED")
                                .reason("Establish incident characteristics")
                                .expectedEvidence(List.of("severity", "affected_endpoints"))
                                .build()
                ))
                .isFallback(false)
                .build();

        when(aiClient.plan(any(AiPlanRequest.class))).thenReturn(aiResponse);

        InvestigationPlan plan = hybridPlanner.plan(context);

        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().get(0).getTaskType()).isEqualTo("INCIDENT_TRIAGE");
        assertThat(plan.getTasks().get(0).getAssignedAgentType()).isEqualTo("incident-triage-agent");
        assertThat(plan.getReasoningSummary()).isEqualTo("Classify incident severity and affected endpoints.");
        assertThat(plan.getNextAction()).isEqualTo(PlanAction.EXECUTE_TASKS);
        verify(aiClient).plan(any(AiPlanRequest.class));
    }

    @Test
    @DisplayName("HybridPlanner: activates Deterministic fallback when AI client signals fallback")
    void plan_AiFallback_UsesDeterministicPlanner() {
        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of())
                .evidence(List.of())
                .build();

        AiPlanResponse fallbackResponse = AiPlanResponse.builder()
                .investigationId(investigation.getId())
                .decision("CONTINUE")
                .reason("AI service down; fallback triggered")
                .isFallback(true)
                .build();

        when(aiClient.plan(any(AiPlanRequest.class))).thenReturn(fallbackResponse);

        InvestigationPlan plan = hybridPlanner.plan(context);

        // Deterministic planner should produce initial triage task
        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().get(0).getTaskType()).isEqualTo("INCIDENT_TRIAGE");
        assertThat(plan.getNextAction()).isEqualTo(PlanAction.EXECUTE_TASKS);
    }
}
