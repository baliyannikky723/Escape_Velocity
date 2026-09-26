package com.incidentmind.planner.core;

import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.planner.model.InvestigationPlan;
import com.incidentmind.planner.model.PlanAction;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationPlannerTest {

    private DeterministicPlanner planner;
    private Incident incident;
    private Investigation investigation;

    @BeforeEach
    void setUp() {
        planner = new DeterministicPlanner();
        incident = Incident.builder()
                .id(UUID.randomUUID())
                .incidentKey("INC-000001")
                .title("Checkout API error rate increase")
                .description("Checkout error rate spiked from 2% to 18% shortly after deployment")
                .serviceName("checkout-service")
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
    @DisplayName("Planner: creates initial triage task when no tasks exist")
    void plan_InitialPlan_CreatesTriageTask() {
        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of())
                .evidence(List.of())
                .build();

        InvestigationPlan plan = planner.plan(context);

        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().get(0).getTaskType()).isEqualTo("INCIDENT_TRIAGE");
        assertThat(plan.getNextAction()).isEqualTo(PlanAction.EXECUTE_TASKS);
        assertThat(plan.isFinalPlan()).isFalse();
    }

    @Test
    @DisplayName("Dynamic Replanning: after Triage completes, Planner dynamically creates Change Analysis task")
    void plan_AfterTriageCompleted_DynamicallyPlansChangeAnalysis() {
        UUID triageTaskId = UUID.randomUUID();
        InvestigationTask triageTask = InvestigationTask.builder()
                .id(triageTaskId)
                .investigationId(investigation.getId())
                .taskType("INCIDENT_TRIAGE")
                .status(TaskStatus.COMPLETED)
                .build();

        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of(triageTask))
                .evidence(List.of())
                .build();

        InvestigationPlan plan = planner.plan(context);

        // Verify dynamic replanning
        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().get(0).getTaskType()).isEqualTo("CHANGE_ANALYSIS");
        assertThat(plan.getTasks().get(0).getParentTaskId()).isEqualTo(triageTaskId);
        assertThat(plan.getTasks().get(0).getAssignedAgentType()).isEqualTo("change-analysis-agent");
    }

    @Test
    @DisplayName("Dynamic Replanning: after Change Analysis completes with Evidence, Planner dynamically creates Dependency Analysis task")
    void plan_AfterChangeAnalysisCompleted_DynamicallyPlansDependencyAnalysis() {
        UUID triageTaskId = UUID.randomUUID();
        InvestigationTask triageTask = InvestigationTask.builder()
                .id(triageTaskId)
                .investigationId(investigation.getId())
                .taskType("INCIDENT_TRIAGE")
                .status(TaskStatus.COMPLETED)
                .build();

        UUID changeTaskId = UUID.randomUUID();
        InvestigationTask changeTask = InvestigationTask.builder()
                .id(changeTaskId)
                .investigationId(investigation.getId())
                .parentTaskId(triageTaskId)
                .taskType("CHANGE_ANALYSIS")
                .status(TaskStatus.COMPLETED)
                .build();

        Evidence commitEvidence = Evidence.builder()
                .id(UUID.randomUUID())
                .investigationId(investigation.getId())
                .taskId(changeTaskId)
                .sourceType("GITHUB")
                .claim("Recent commit pushed to master")
                .build();

        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of(triageTask, changeTask))
                .evidence(List.of(commitEvidence))
                .build();

        InvestigationPlan plan = planner.plan(context);

        assertThat(plan.getTasks()).hasSize(1);
        assertThat(plan.getTasks().get(0).getTaskType()).isEqualTo("DEPENDENCY_ANALYSIS");
        assertThat(plan.getTasks().get(0).getParentTaskId()).isEqualTo(changeTaskId);
        assertThat(plan.getTasks().get(0).getAssignedAgentType()).isEqualTo("dependency-analysis-agent");
    }

    @Test
    @DisplayName("Planner: prevents creating duplicate tasks when task type already exists")
    void plan_PreventsDuplicateTasks() {
        InvestigationTask triageTask = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .investigationId(investigation.getId())
                .taskType("INCIDENT_TRIAGE")
                .status(TaskStatus.RUNNING)
                .build();

        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of(triageTask))
                .evidence(List.of())
                .build();

        InvestigationPlan plan = planner.plan(context);

        // Does not re-create INCIDENT_TRIAGE
        assertThat(plan.getTasks()).isEmpty();
        assertThat(plan.getNextAction()).isEqualTo(PlanAction.AWAIT_EVIDENCE);
    }

    @Test
    @DisplayName("Planner: marks plan complete when all planned tasks are finished")
    void plan_AllTasksCompleted_ReturnsNoFurtherTasks() {
        InvestigationTask t1 = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("INCIDENT_TRIAGE")
                .status(TaskStatus.COMPLETED)
                .build();
        InvestigationTask t2 = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("CHANGE_ANALYSIS")
                .status(TaskStatus.COMPLETED)
                .build();
        InvestigationTask t3 = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("DEPENDENCY_ANALYSIS")
                .status(TaskStatus.COMPLETED)
                .build();

        PlannerContext context = PlannerContext.builder()
                .incident(incident)
                .investigation(investigation)
                .existingTasks(List.of(t1, t2, t3))
                .evidence(List.of())
                .build();

        InvestigationPlan plan = planner.plan(context);

        assertThat(plan.getTasks()).isEmpty();
        assertThat(plan.getNextAction()).isEqualTo(PlanAction.NO_FURTHER_TASKS);
        assertThat(plan.isFinalPlan()).isTrue();
    }
}
