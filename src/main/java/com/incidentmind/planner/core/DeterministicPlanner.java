package com.incidentmind.planner.core;

import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.planner.model.InvestigationPlan;
import com.incidentmind.planner.model.PlanAction;
import com.incidentmind.planner.model.PlannedTask;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component("deterministicPlanner")
public class DeterministicPlanner implements InvestigationPlanner {

    @Override
    public InvestigationPlan plan(PlannerContext context) {
        Incident incident = context.getIncident();
        List<InvestigationTask> existingTasks = context.getExistingTasks() != null ? context.getExistingTasks() : List.of();
        List<Evidence> evidenceList = context.getEvidence() != null ? context.getEvidence() : List.of();

        Set<String> existingTaskTypes = existingTasks.stream()
                .map(t -> t.getTaskType().toUpperCase())
                .collect(Collectors.toSet());

        log.debug("DeterministicPlanner evaluating: existingTasksCount={}, evidenceCount={}",
                existingTasks.size(), evidenceList.size());

        List<PlannedTask> newTasks = new ArrayList<>();
        String reasoning;

        // Step 0: Initial planning (no existing tasks)
        if (existingTasks.isEmpty()) {
            PlannedTask triageTask = PlannedTask.builder()
                    .taskType("INCIDENT_TRIAGE")
                    .title("Incident Triage & Failure Pattern Analysis")
                    .description(String.format("Perform initial diagnostic triage on reported incident: '%s'",
                            incident != null ? incident.getTitle() : "Unknown"))
                    .priority(TaskPriority.HIGH)
                    .requiredCapability("INCIDENT_TRIAGE")
                    .assignedAgentType("incident-triage-agent")
                    .toolRequirement(com.incidentmind.planner.model.ToolRequirement.NO_TOOL_REQUIRED)
                    .toolSelectionReason("Incident payload context is sufficient to generate diagnostic hypotheses without external tool queries")
                    .build();

            newTasks.add(triageTask);
            reasoning = "Initiating investigation plan with Incident Triage to formulate hypotheses from incident context.";

            return InvestigationPlan.builder()
                    .reasoningSummary(reasoning)
                    .tasks(newTasks)
                    .nextAction(PlanAction.EXECUTE_TASKS)
                    .finalPlan(false)
                    .build();
        }

        // Check completion state of existing tasks
        Optional<InvestigationTask> triageTaskOpt = existingTasks.stream()
                .filter(t -> "INCIDENT_TRIAGE".equalsIgnoreCase(t.getTaskType()))
                .findFirst();

        Optional<InvestigationTask> changeAnalysisOpt = existingTasks.stream()
                .filter(t -> "CHANGE_ANALYSIS".equalsIgnoreCase(t.getTaskType())
                        || "INVESTIGATE_RECENT_COMMITS".equalsIgnoreCase(t.getTaskType()))
                .findFirst();

        Optional<InvestigationTask> pullRequestOpt = existingTasks.stream()
                .filter(t -> "INVESTIGATE_PULL_REQUESTS".equalsIgnoreCase(t.getTaskType()))
                .findFirst();

        Optional<InvestigationTask> dependencyAnalysisOpt = existingTasks.stream()
                .filter(t -> "DEPENDENCY_ANALYSIS".equalsIgnoreCase(t.getTaskType()))
                .findFirst();

        // Step 1: Dynamic replanning after Triage completion
        if (triageTaskOpt.isPresent() && triageTaskOpt.get().getStatus() == TaskStatus.COMPLETED) {
            UUID triageTaskId = triageTaskOpt.get().getId();

            if (!existingTaskTypes.contains("CHANGE_ANALYSIS") && !existingTaskTypes.contains("INVESTIGATE_RECENT_COMMITS")) {
                PlannedTask changeTask = PlannedTask.builder()
                        .taskType("CHANGE_ANALYSIS")
                        .title("Investigate Recent Commits & Code Changes")
                        .description("Query GitHub ToolGateway to inspect recent repository commits for potential deployment regressions.")
                        .priority(TaskPriority.HIGH)
                        .requiredCapability("CHANGE_ANALYSIS")
                        .parentTaskId(triageTaskId)
                        .assignedAgentType("change-analysis-agent")
                        .toolRequirement(com.incidentmind.planner.model.ToolRequirement.TOOL_REQUIRED)
                        .suggestedToolName("github.get_recent_commits")
                        .toolSelectionReason("Query GitHub commit history to observe repository state prior to incident")
                        .build();

                newTasks.add(changeTask);
                reasoning = "Triage completed; dynamically generating Change Analysis task to inspect repository history.";

                return InvestigationPlan.builder()
                        .reasoningSummary(reasoning)
                        .tasks(newTasks)
                        .nextAction(PlanAction.EXECUTE_TASKS)
                        .finalPlan(false)
                        .build();
            }
        }

        // Step 2A: Critic-driven replanning if Change Analysis was rejected / failed with recommendation to investigate PRs
        if (changeAnalysisOpt.isPresent() && changeAnalysisOpt.get().getStatus() == TaskStatus.FAILED && !existingTaskTypes.contains("INVESTIGATE_PULL_REQUESTS")) {
            PlannedTask prTask = PlannedTask.builder()
                    .taskType("INVESTIGATE_PULL_REQUESTS")
                    .title("Investigate Pull Requests & Code Review Evidence")
                    .description("Query GitHub pull requests to gather deeper code review, CI status, and PR description evidence after initial change analysis rejection.")
                    .priority(TaskPriority.HIGH)
                    .requiredCapability("CHANGE_ANALYSIS")
                    .parentTaskId(null) // Independent of failed task
                    .assignedAgentType("change-analysis-agent")
                    .toolRequirement(com.incidentmind.planner.model.ToolRequirement.TOOL_REQUIRED)
                    .suggestedToolName("github.get_pull_requests")
                    .toolSelectionReason("Gather deeper PR review evidence as recommended by Critic validation")
                    .build();

            newTasks.add(prTask);
            reasoning = "Change analysis was rejected by Critic or failed; dynamically creating Pull Request investigation task as requested by Critic.";

            return InvestigationPlan.builder()
                    .reasoningSummary(reasoning)
                    .tasks(newTasks)
                    .nextAction(PlanAction.EXECUTE_TASKS)
                    .finalPlan(false)
                    .build();
        }

        // Step 2B: Dynamic replanning after Change Analysis / Pull Request completion
        boolean changePhaseComplete = (changeAnalysisOpt.isPresent() && changeAnalysisOpt.get().getStatus() == TaskStatus.COMPLETED)
                || (pullRequestOpt.isPresent() && (pullRequestOpt.get().getStatus() == TaskStatus.COMPLETED || pullRequestOpt.get().getStatus() == TaskStatus.FAILED))
                || (changeAnalysisOpt.isPresent() && changeAnalysisOpt.get().getStatus() == TaskStatus.FAILED && existingTaskTypes.contains("INVESTIGATE_PULL_REQUESTS"));

        if (changePhaseComplete && !existingTaskTypes.contains("DEPENDENCY_ANALYSIS")) {
            UUID parentId = changeAnalysisOpt.map(InvestigationTask::getId).orElse(null);
            boolean wasFailed = changeAnalysisOpt.isPresent() && changeAnalysisOpt.get().getStatus() == TaskStatus.FAILED;

            PlannedTask depTask = PlannedTask.builder()
                    .taskType("DEPENDENCY_ANALYSIS")
                    .title("Investigate Service Dependencies & Downstream Telemetry")
                    .description("Analyze service dependency graph, latency metrics, and error rates across upstream and downstream payment services.")
                    .priority(TaskPriority.MEDIUM)
                    .requiredCapability("DEPENDENCY_ANALYSIS")
                    .parentTaskId(wasFailed ? null : parentId)
                    .assignedAgentType("dependency-analysis-agent")
                    .toolRequirement(com.incidentmind.planner.model.ToolRequirement.NO_TOOL_REQUIRED)
                    .toolSelectionReason("Examine payment architecture boundaries and downstream telemetry requirements")
                    .build();

            newTasks.add(depTask);
            reasoning = wasFailed
                    ? "Change analysis failed or was rejected; dynamically replanning alternative investigation path with Dependency Analysis."
                    : "Code change evidence gathered; dynamically generating Dependency Analysis task to verify dependency boundaries.";

            return InvestigationPlan.builder()
                    .reasoningSummary(reasoning)
                    .tasks(newTasks)
                    .nextAction(PlanAction.EXECUTE_TASKS)
                    .finalPlan(false)
                    .build();
        }

        // Step 3: Check if all existing tasks are completed
        boolean allCompleted = existingTasks.stream()
                .allMatch(t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.FAILED || t.getStatus() == TaskStatus.SKIPPED);

        if (allCompleted) {
            reasoning = String.format("Investigation phase plan complete. Evaluated %d task(s) and collected %d evidence record(s). Ready for subsequent evaluation.",
                    existingTasks.size(), evidenceList.size());
            return InvestigationPlan.builder()
                    .reasoningSummary(reasoning)
                    .tasks(List.of())
                    .nextAction(PlanAction.NO_FURTHER_TASKS)
                    .finalPlan(true)
                    .build();
        }

        // If tasks are currently pending/running
        reasoning = "Awaiting execution of currently planned tasks before further replanning.";
        return InvestigationPlan.builder()
                .reasoningSummary(reasoning)
                .tasks(List.of())
                .nextAction(PlanAction.AWAIT_EVIDENCE)
                .finalPlan(false)
                .build();
    }
}
