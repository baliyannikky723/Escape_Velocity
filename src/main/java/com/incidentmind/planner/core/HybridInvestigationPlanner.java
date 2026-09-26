package com.incidentmind.planner.core;

import com.incidentmind.ai.client.AiIntelligenceClient;
import com.incidentmind.ai.config.AiServiceProperties;
import com.incidentmind.ai.dto.AiPlanRequest;
import com.incidentmind.ai.dto.AiPlanResponse;
import com.incidentmind.ai.dto.AiPlannedActionDto;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.filter.CorrelationContext;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.planner.model.InvestigationPlan;
import com.incidentmind.planner.model.PlanAction;
import com.incidentmind.planner.model.PlannedTask;
import com.incidentmind.planner.model.ToolRequirement;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
public class HybridInvestigationPlanner implements InvestigationPlanner {

    private final DeterministicPlanner deterministicPlanner;
    private final AiIntelligenceClient aiClient;
    private final AiServiceProperties properties;
    private final AuditService auditService;

    public HybridInvestigationPlanner(
            DeterministicPlanner deterministicPlanner,
            AiIntelligenceClient aiClient,
            AiServiceProperties properties,
            AuditService auditService
    ) {
        this.deterministicPlanner = deterministicPlanner;
        this.aiClient = aiClient;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    public InvestigationPlan plan(PlannerContext context) {
        UUID investigationId = context.getInvestigation() != null ? context.getInvestigation().getId() : null;
        UUID correlationId = CorrelationContext.getCorrelationIdAsUuid();

        if (!properties.isEnabled()) {
            return deterministicPlanner.plan(context);
        }

        try {
            // Record LLM_PLANNER_STARTED
            auditService.recordEvent(
                    investigationId,
                    null,
                    null,
                    AuditEventType.LLM_PLANNER_STARTED,
                    ActorType.ORCHESTRATOR,
                    "hybrid-investigation-planner",
                    Map.of("service", "FastAPI AI Intelligence"),
                    correlationId
            );

            AiPlanRequest request = buildAiPlanRequest(context, investigationId);
            AiPlanResponse aiResponse = aiClient.plan(request);

            if (aiResponse == null || aiResponse.isFallback()) {
                log.info("LLM Planner indicated fallback; activating DeterministicPlanner");
                recordFallbackAudit(investigationId, aiResponse != null ? aiResponse.getReason() : "Null AI response", correlationId);
                return deterministicPlanner.plan(context);
            }

            // Convert structured AI plan actions to InvestigationPlan
            InvestigationPlan plan = convertToInvestigationPlan(aiResponse, context);

            // Record LLM_PLANNER_COMPLETED
            auditService.recordEvent(
                    investigationId,
                    null,
                    null,
                    AuditEventType.LLM_PLANNER_COMPLETED,
                    ActorType.ORCHESTRATOR,
                    "hybrid-investigation-planner",
                    Map.of("decision", aiResponse.getDecision(), "actionsCount", plan.getTasks().size()),
                    correlationId
            );

            return plan;

        } catch (Exception ex) {
            log.warn("Exception during LLM planning; falling back to DeterministicPlanner: {}", ex.getMessage());
            recordFallbackAudit(investigationId, ex.getMessage(), correlationId);
            return deterministicPlanner.plan(context);
        }
    }

    private void recordFallbackAudit(UUID investigationId, String reason, UUID correlationId) {
        auditService.recordEvent(
                investigationId,
                null,
                null,
                AuditEventType.LLM_FALLBACK_ACTIVATED,
                ActorType.ORCHESTRATOR,
                "hybrid-investigation-planner",
                Map.of("reason", reason != null ? reason : "Unknown fallback reason"),
                correlationId
        );
    }

    private AiPlanRequest buildAiPlanRequest(PlannerContext context, UUID investigationId) {
        Incident incident = context.getIncident();
        Map<String, Object> incidentMap = new HashMap<>();
        if (incident != null) {
            incidentMap.put("title", incident.getTitle());
            incidentMap.put("serviceName", incident.getServiceName());
            incidentMap.put("environment", incident.getEnvironment());
            incidentMap.put("severity", incident.getSeverity() != null ? incident.getSeverity().name() : "P1");
            incidentMap.put("description", incident.getDescription());
        }

        List<Map<String, Object>> tasksList = new ArrayList<>();
        if (context.getExistingTasks() != null) {
            for (InvestigationTask t : context.getExistingTasks()) {
                Map<String, Object> tm = new HashMap<>();
                tm.put("id", t.getId().toString());
                tm.put("taskType", t.getTaskType());
                tm.put("status", t.getStatus() != null ? t.getStatus().name() : "PENDING");
                tm.put("assignedAgent", t.getAssignedAgentType());
                tasksList.add(tm);
            }
        }

        List<Map<String, Object>> evidenceList = new ArrayList<>();
        if (context.getEvidence() != null) {
            for (Evidence e : context.getEvidence()) {
                Map<String, Object> em = new HashMap<>();
                em.put("claim", e.getClaim());
                em.put("sourceType", e.getSourceType());
                evidenceList.add(em);
            }
        }

        Map<String, Object> constraints = new HashMap<>();
        if (context.getInvestigation() != null) {
            constraints.put("maxTasks", context.getInvestigation().getMaxTasks() != null ? context.getInvestigation().getMaxTasks() : 10);
            constraints.put("maxRuntimeSeconds", context.getInvestigation().getMaxRuntimeSeconds() != null ? context.getInvestigation().getMaxRuntimeSeconds() : 120);
        }

        return AiPlanRequest.builder()
                .investigationId(investigationId)
                .incident(incidentMap)
                .tasks(tasksList)
                .evidence(evidenceList)
                .availableAgents(List.of("incident-triage-agent", "change-analysis-agent", "dependency-analysis-agent"))
                .availableTools(List.of("github.get_repository", "github.get_recent_commits", "github.get_pull_requests"))
                .constraints(constraints)
                .build();
    }

    private InvestigationPlan convertToInvestigationPlan(AiPlanResponse aiResponse, PlannerContext context) {
        List<PlannedTask> plannedTasks = new ArrayList<>();

        if (aiResponse.getActions() != null) {
            for (AiPlannedActionDto action : aiResponse.getActions()) {
                if ("CREATE_TASK".equalsIgnoreCase(action.getAction())) {
                    TaskPriority priority = TaskPriority.HIGH;
                    try {
                        if (action.getPriority() != null) {
                            priority = TaskPriority.valueOf(action.getPriority().toUpperCase());
                        }
                    } catch (Exception ignored) {}

                    UUID parentId = null;
                    if (action.getParentTaskId() != null) {
                        try {
                            parentId = UUID.fromString(action.getParentTaskId());
                        } catch (Exception ignored) {}
                    }

                    ToolRequirement toolReq = "TOOL_REQUIRED".equalsIgnoreCase(action.getToolRequirement()) || "GITHUB_API".equalsIgnoreCase(action.getToolRequirement())
                            ? ToolRequirement.TOOL_REQUIRED
                            : ToolRequirement.NO_TOOL_REQUIRED;

                    plannedTasks.add(PlannedTask.builder()
                            .taskType(action.getTaskType())
                            .title(action.getReason() != null ? action.getReason() : "AI Planned Investigation Task")
                            .description(action.getReason())
                            .priority(priority)
                            .requiredCapability(action.getTaskType())
                            .parentTaskId(parentId)
                            .assignedAgentType(action.getAgentType())
                            .toolRequirement(toolReq)
                            .suggestedToolName(action.getSuggestedTool())
                            .toolSelectionReason(action.getReason())
                            .build());
                }
            }
        }
        PlanAction planAction = PlanAction.EXECUTE_TASKS;
        if ("WAIT".equalsIgnoreCase(aiResponse.getDecision())) {
            planAction = PlanAction.AWAIT_EVIDENCE;
        } else if ("STOP_SUCCESS".equalsIgnoreCase(aiResponse.getDecision()) || "HUMAN_REVIEW_REQUIRED".equalsIgnoreCase(aiResponse.getDecision()) || plannedTasks.isEmpty()) {
            planAction = PlanAction.NO_FURTHER_TASKS;
        }

        return InvestigationPlan.builder()
                .reasoningSummary(aiResponse.getReason())
                .tasks(plannedTasks)
                .nextAction(planAction)
                .finalPlan("STOP_SUCCESS".equalsIgnoreCase(aiResponse.getDecision()))
                .build();
    }
}
