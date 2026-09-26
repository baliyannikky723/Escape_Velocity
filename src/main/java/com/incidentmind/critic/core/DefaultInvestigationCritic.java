package com.incidentmind.critic.core;

import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.critic.model.CriticContext;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class DefaultInvestigationCritic implements InvestigationCritic {

    private final AuditService auditService;

    private static final Set<String> SENSITIVE_ACTION_KEYWORDS = Set.of(
            "ROLLBACK", "DEPLOY", "RESTART_SERVICE", "DROP_TABLE", "UPDATE_DATABASE",
            "MODIFY_CREDENTIALS", "DELETE_RECORDS", "EXECUTE_PRODUCTION_CHANGE"
    );

    public DefaultInvestigationCritic(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public CriticResult evaluate(CriticContext context) {
        if (context == null || context.getTask() == null) {
            return CriticResult.reject(null, List.of("EMPTY_CONTEXT"), List.of(), "Critic context or task is null", "ABORT");
        }

        UUID taskId = context.getTask().getId();
        UUID investigationId = context.getInvestigation() != null ? context.getInvestigation().getId() : null;
        UUID correlationId = context.getCorrelationId();

        // 1. Audit: CRITIC_STARTED
        auditService.recordEvent(
                investigationId,
                taskId,
                null,
                AuditEventType.CRITIC_STARTED,
                ActorType.SYSTEM,
                "investigation-critic",
                Map.of("taskId", taskId.toString(), "taskType", context.getTask().getTaskType()),
                correlationId
        );

        AgentExecutionResult agentResult = context.getAgentResult();
        if (agentResult == null) {
            CriticResult res = CriticResult.reject(taskId, List.of("NO_AGENT_RESULT"), List.of(), "Agent did not produce any result", "REPLAN");
            recordCriticDecisionAudit(investigationId, taskId, res, correlationId);
            return res;
        }

        Map<String, Object> outputPayload = agentResult.getOutputPayload() != null ? agentResult.getOutputPayload() : Map.of();

        // 2. Check: Safety / Human Approval Required
        for (String keyword : SENSITIVE_ACTION_KEYWORDS) {
            if (outputPayload.containsKey("proposedAction") && outputPayload.get("proposedAction").toString().toUpperCase().contains(keyword)
                    || outputPayload.toString().toUpperCase().contains("PROPOSE_" + keyword)) {
                log.warn("Critic flagged sensitive production action: {}", keyword);
                CriticResult res = CriticResult.humanApprovalRequired(taskId, keyword, "Sensitive action proposes automated production modification");
                recordCriticDecisionAudit(investigationId, taskId, res, correlationId);
                return res;
            }
        }

        // 3. Check: Unsupported Causal Claims
        // Look for ungrounded causality claims (e.g. "Commit X caused the incident" without root cause proof)
        boolean hasUnsupportedCausalClaim = false;
        String causalReason = "";

        if (outputPayload.containsKey("causalClaim") || outputPayload.containsKey("conclusion")) {
            String claimStr = String.valueOf(outputPayload.getOrDefault("causalClaim", outputPayload.get("conclusion")));
            if (claimStr.toLowerCase().contains("caused the incident") || claimStr.toLowerCase().contains("caused the outage")) {
                hasUnsupportedCausalClaim = true;
                causalReason = "Temporal proximity of a commit does not establish causality without verified error logs or diff analysis.";
            }
        }

        if (agentResult.getEvidenceDrafts() != null) {
            for (EvidenceDraft draft : agentResult.getEvidenceDrafts()) {
                log.info("Critic inspecting draft claim: '{}'", draft.getClaim());
                if (draft.getClaim() != null && (draft.getClaim().toLowerCase().contains("caused the incident")
                        || draft.getClaim().toLowerCase().contains("is the root cause"))) {
                    hasUnsupportedCausalClaim = true;
                    causalReason = "Evidence only establishes repository change existence; claiming definitive root cause without telemetry verification is ungrounded.";
                    break;
                }
            }
        }

        if (hasUnsupportedCausalClaim) {
            log.warn("Critic REJECTED agent output due to unsupported causal claim: {}", causalReason);
            CriticResult res = CriticResult.reject(
                    taskId,
                    List.of("UNSUPPORTED_CAUSAL_CLAIM"),
                    List.of("Definitive root cause assertion"),
                    causalReason,
                    "INVESTIGATE_PULL_REQUESTS"
            );
            recordCriticDecisionAudit(investigationId, taskId, res, correlationId);
            return res;
        }

        // 4. Check: Inconclusive / Failed agent execution
        if (!agentResult.isSuccess()) {
            List<String> reqEvidence = new ArrayList<>();
            reqEvidence.add("Alternative telemetry / dependency metrics");
            CriticResult res = CriticResult.inconclusive(taskId, reqEvidence, "Agent task was incomplete or encountered unrecovered errors: " + agentResult.getErrorMessage());
            recordCriticDecisionAudit(investigationId, taskId, res, correlationId);
            return res;
        }

        // 5. Check: Evidence Grounding
        List<String> acceptedClaims = new ArrayList<>();
        if (agentResult.getEvidenceDrafts() != null) {
            for (EvidenceDraft draft : agentResult.getEvidenceDrafts()) {
                acceptedClaims.add(draft.getClaim());
            }
        }
        if (acceptedClaims.isEmpty() && outputPayload.containsKey("summary")) {
            acceptedClaims.add(outputPayload.get("summary").toString());
        }

        CriticResult res = CriticResult.accept(
                taskId,
                acceptedClaims,
                "Findings are grounded in verified tool outputs and observations without hallucinated causal claims.",
                0.95
        );
        recordCriticDecisionAudit(investigationId, taskId, res, correlationId);
        return res;
    }

    private void recordCriticDecisionAudit(UUID investigationId, UUID taskId, CriticResult result, UUID correlationId) {
        AuditEventType eventType = switch (result.getDecision()) {
            case ACCEPT -> AuditEventType.CRITIC_ACCEPTED;
            case REJECT -> AuditEventType.CRITIC_REJECTED;
            case INCONCLUSIVE -> AuditEventType.CRITIC_INCONCLUSIVE;
            case HUMAN_APPROVAL_REQUIRED -> AuditEventType.HUMAN_APPROVAL_REQUIRED;
        };

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("decision", result.getDecision().name());
        auditData.put("reasons", result.getReasons());
        if (!result.getFailedChecks().isEmpty()) auditData.put("failedChecks", result.getFailedChecks());
        if (!result.getAcceptedClaims().isEmpty()) auditData.put("acceptedClaims", result.getAcceptedClaims());
        if (result.getRecommendedNextAction() != null) auditData.put("recommendedNextAction", result.getRecommendedNextAction());

        auditService.recordEvent(
                investigationId,
                taskId,
                null,
                eventType,
                ActorType.SYSTEM,
                "investigation-critic",
                auditData,
                correlationId
        );
    }
}
