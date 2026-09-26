package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineEventDto {

    private UUID eventId;
    private Instant timestamp;
    private AuditEventType eventType;
    private UUID taskId;
    private UUID agentRunId;
    private ActorType actorType;
    private String actorId;
    private String description;
    private UUID correlationId;
    private Map<String, Object> data;

    public static TimelineEventDto fromEntity(AuditEvent event) {
        if (event == null) return null;

        String description = generateDescription(event);

        return TimelineEventDto.builder()
                .eventId(event.getId())
                .timestamp(event.getOccurredAt())
                .eventType(event.getEventType())
                .taskId(event.getTaskId())
                .agentRunId(event.getAgentRunId())
                .actorType(event.getActorType())
                .actorId(event.getActorId())
                .description(description)
                .correlationId(event.getCorrelationId())
                .data(event.getEventData())
                .build();
    }

    private static String generateDescription(AuditEvent event) {
        if (event.getEventType() == null) return "Investigation audit event";
        return switch (event.getEventType()) {
            case INCIDENT_CREATED -> "Incident record created";
            case INCIDENT_UPDATED -> "Incident record updated";
            case INCIDENT_RESOLVED -> "Incident marked as resolved";
            case INVESTIGATION_CREATED -> "Investigation created";
            case INVESTIGATION_STARTED -> "Investigation started and initialized";
            case INVESTIGATION_STOPPED -> "Investigation stopped by policy or engineer";
            case INVESTIGATION_COMPLETED -> "Investigation completed successfully";
            case INVESTIGATION_FAILED -> "Investigation failed";
            case PLANNER_STARTED -> "Planner evaluated incident and evidence state";
            case PLAN_CREATED -> "Planner created dynamic execution plan";
            case TASK_CREATED -> "New investigation task scheduled dynamically";
            case TASK_STARTED -> "Task execution started";
            case TASK_COMPLETED -> "Task completed successfully";
            case TASK_FAILED -> "Task execution failed";
            case EXECUTOR_STARTED -> "Executor initiated task execution";
            case AGENT_STARTED -> "Specialized agent began task analysis";
            case AGENT_COMPLETED -> "Specialized agent completed execution";
            case AGENT_FAILED -> "Specialized agent execution failed";
            case TOOL_CALL_STARTED -> "ToolGateway invoked external tool";
            case TOOL_CALL_COMPLETED -> "ToolGateway completed external tool invocation";
            case TOOL_CALL_FAILED -> "External tool invocation failed";
            case EVIDENCE_CREATED -> "New verified factual evidence drafted and saved";
            case CRITIC_STARTED -> "Critic began evaluating agent findings";
            case CRITIC_ACCEPTED -> "Critic ACCEPTED agent findings as grounded in evidence";
            case CRITIC_REJECTED -> "Critic REJECTED ungrounded or causal claim";
            case CRITIC_INCONCLUSIVE -> "Critic marked findings INCONCLUSIVE; more evidence required";
            case HUMAN_APPROVAL_REQUIRED -> "Sensitive production action flagged; human engineer approval required";
            case RECOVERY_STARTED -> "Recovery engine initiated resilience strategy";
            case RECOVERY_DECISION -> "Recovery engine selected recovery policy decision";
            case RECOVERY_SUCCEEDED -> "Recovery strategy succeeded";
            case RECOVERY_FAILED -> "Recovery attempt failed";
            case RECOVERY_EXHAUSTED -> "Recovery retry budget exhausted";
            case RECOVERY_COMPLETED -> "Recovery process completed";
            case STOPPING_CHECK -> "Stopping policy evaluated investigation boundaries";
            case LLM_PLANNER_STARTED -> "FastAPI AI service requested for LLM planner reasoning";
            case LLM_PLANNER_COMPLETED -> "FastAPI AI service returned structured investigation plan";
            case LLM_PLANNER_FAILED -> "FastAPI AI service planner call encountered error or timeout";
            case LLM_CRITIC_STARTED -> "FastAPI AI service requested for LLM critic validation";
            case LLM_CRITIC_COMPLETED -> "FastAPI AI service critic completed finding evaluation";
            case LLM_CRITIC_REJECTED -> "FastAPI AI critic rejected ungrounded agent claim";
            case LLM_FALLBACK_ACTIVATED -> "LLM unavailable or exhausted; activated deterministic planner fallback";
            case PLAN_RECONCILIATION -> "Plan reconciled dynamically based on updated blackboard findings";
            case HUMAN_REVIEW_REQUIRED -> "Human engineer review required due to repeated critic/planning failures";
            case HUMAN_ACTION_RECEIVED -> "Human engineer action received and recorded in investigation audit trail";
            default -> event.getEventType().name();
        };
    }
}
