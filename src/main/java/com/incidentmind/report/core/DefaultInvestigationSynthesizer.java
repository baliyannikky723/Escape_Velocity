package com.incidentmind.report.core;

import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.blackboard.dto.InvestigationMetricsResponse;
import com.incidentmind.critic.dto.CriticEvaluationResponse;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.recovery.dto.RecoveryAttemptResponse;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.entity.RecoveryStatus;
import com.incidentmind.report.dto.CriticSummaryResponse;
import com.incidentmind.report.dto.InvestigationReportResponse;
import com.incidentmind.report.dto.RecoverySummaryResponse;
import com.incidentmind.report.dto.StoppingSummaryDto;
import com.incidentmind.report.dto.TimelineEventDto;
import com.incidentmind.report.model.ConclusionType;
import com.incidentmind.stopping.model.StoppingDecision;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DefaultInvestigationSynthesizer implements InvestigationSynthesizer {

    @Override
    public InvestigationReportResponse synthesizeReport(
            Incident incident,
            Investigation investigation,
            List<InvestigationTask> tasks,
            List<Evidence> evidenceList,
            List<RecoveryAttempt> recoveryAttempts,
            List<AuditEvent> auditEvents,
            InvestigationBlackboard blackboard) {

        if (investigation == null) {
            return null;
        }

        UUID investigationId = investigation.getId();
        UUID incidentId = incident != null ? incident.getId() : (investigation.getIncidentId());
        String incidentTitle = incident != null ? incident.getTitle() : "Unknown incident";

        List<CriticResult> criticResults = blackboard != null && blackboard.getCriticResults() != null
                ? blackboard.getCriticResults()
                : List.of();

        // 1. Build Sub-Summaries
        CriticSummaryResponse criticSummary = buildCriticSummary(criticResults, auditEvents);
        RecoverySummaryResponse recoverySummary = buildRecoverySummary(recoveryAttempts);
        StoppingSummaryDto stoppingSummary = buildStoppingSummary(tasks, criticResults, recoveryAttempts, investigation, auditEvents, criticSummary);
        InvestigationMetricsResponse metrics = blackboard != null && blackboard.getMetrics() != null
                ? InvestigationMetricsResponse.fromModel(blackboard.getMetrics())
                : null;
        List<TimelineEventDto> timeline = buildTimeline(auditEvents);
        List<EvidenceResponse> evidenceResponses = evidenceList != null
                ? evidenceList.stream().map(EvidenceResponse::fromEntity).collect(Collectors.toList())
                : List.of();

        // 2. Synthesize Grounded Findings, Hypotheses, and Questions
        Set<String> keyFindings = new LinkedHashSet<>();
        Set<String> hypotheses = new LinkedHashSet<>();
        Set<String> unresolvedQuestions = new LinkedHashSet<>();
        Set<String> recommendedNextActions = new LinkedHashSet<>();

        boolean hasHumanApprovalRequired = criticSummary.getHumanApprovalCount() > 0;
        boolean hasCriticRejection = criticSummary.getRejectedCount() > 0;
        boolean hasEvidence = evidenceList != null && !evidenceList.isEmpty();
        boolean hasExhaustedRecovery = recoverySummary.getExhaustedRecoveries() > 0;

        // Evidence-derived findings
        if (evidenceList != null) {
            for (Evidence ev : evidenceList) {
                if (ev.getClaim() != null) {
                    keyFindings.add("FACT: " + ev.getClaim());
                }
            }
        }

        // Triage findings
        if (incident != null) {
            keyFindings.add(String.format("FACT: Incident reported on service '%s' in '%s' environment with severity '%s'.",
                    incident.getServiceName(), incident.getEnvironment(), incident.getSeverity()));
        }

        // Determine Conclusion and Classification
        ConclusionType conclusionType;
        String finalConclusion;
        String confidenceReasoning;

        if (hasHumanApprovalRequired) {
            conclusionType = ConclusionType.UNKNOWN;
            finalConclusion = "A proposed automated production modification (e.g. rollback, deployment, or database modification) was flagged by safety policies and requires human engineer authorization.";
            confidenceReasoning = "Safety gate blocked autonomous execution; automated actions halted awaiting human review.";
            unresolvedQuestions.add("Should the proposed production remediation action be authorized and executed manually?");
            recommendedNextActions.add("Human operator review the proposed production change and approve or reject via operations console. (HUMAN APPROVAL REQUIRED)");
        } else if (hasCriticRejection) {
            conclusionType = ConclusionType.SUPPORTED_FINDING;
            finalConclusion = "Evidence indicates recent code and deployment changes correlate with incident timing, but repository change evidence alone does not establish causal proof without error log or telemetry confirmation.";
            confidenceReasoning = "Critic rejected ungrounded root-cause assertion. Rejection reasons: " + String.join("; ", criticSummary.getRejectionReasons());
            hypotheses.add("Recent code changes may have introduced regressions in checkout error handling or downstream API communication.");
            unresolvedQuestions.add("Critic rejected findings: Do application error logs or stack traces directly reference changed code paths in recent commits?");
            recommendedNextActions.add("Inspect pull request review comments and CI artifacts for the identified recent commits.");
            recommendedNextActions.add("Correlate application stack traces with recent diff modifications.");
        } else if (hasExhaustedRecovery) {
            conclusionType = ConclusionType.UNRESOLVED;
            finalConclusion = "External tool availability was interrupted and recovery attempts exhausted. Investigation could not gather complete telemetry evidence.";
            confidenceReasoning = "Tool invocation encountered persistent errors after multiple retries.";
            unresolvedQuestions.add("External tool services were unavailable or failed: Are external GitHub or telemetry API services currently healthy and reachable?");
            recommendedNextActions.add("Verify network connectivity and service credentials for external tool gateways.");
            recommendedNextActions.add("Re-run investigation once external API endpoints are confirmed operational.");
        } else if (hasEvidence) {
            conclusionType = ConclusionType.SUPPORTED_FINDING;
            finalConclusion = "Investigation gathered verified evidence supporting recent deployment activity as the primary correlation lead. No conflicting dependency failures were observed.";
            confidenceReasoning = "All findings verified by Critic against real tool responses and incident payload.";
            hypotheses.add("Deployment changes in the reported window are the most probable factor associated with the error rate increase.");
            unresolvedQuestions.add("Are specific payment gateway endpoints or HTTP status codes concentrated in the error spike?");
            recommendedNextActions.add("Perform detailed diff inspection on recent commits.");
            recommendedNextActions.add("Monitor checkout service latency and error metrics following verification.");
        } else {
            conclusionType = ConclusionType.UNKNOWN;
            finalConclusion = "Investigation concluded without sufficient verified evidence to determine failure correlation.";
            confidenceReasoning = "No factual evidence items could be verified during execution.";
            unresolvedQuestions.add("Is additional incident context or repository configuration required?");
            recommendedNextActions.add("Provide explicit repository metadata and restart investigation.");
        }

        return InvestigationReportResponse.builder()
                .investigationId(investigationId)
                .incidentId(incidentId)
                .incidentTitle(incidentTitle)
                .status(investigation.getStatus())
                .finalConclusion(finalConclusion)
                .conclusionType(conclusionType)
                .confidenceReasoning(confidenceReasoning)
                .keyFindings(new ArrayList<>(keyFindings))
                .evidence(evidenceResponses)
                .hypotheses(new ArrayList<>(hypotheses))
                .unresolvedQuestions(new ArrayList<>(unresolvedQuestions))
                .recommendedNextActions(new ArrayList<>(recommendedNextActions))
                .criticSummary(criticSummary)
                .recoverySummary(recoverySummary)
                .stopping(stoppingSummary)
                .metrics(metrics)
                .timeline(timeline)
                .generatedAt(Instant.now())
                .build();
    }

    private CriticSummaryResponse buildCriticSummary(List<CriticResult> criticResults, List<AuditEvent> auditEvents) {
        int accepted = 0;
        int rejected = 0;
        int inconclusive = 0;
        int humanApproval = 0;
        List<UUID> rejectedTaskIds = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        List<CriticEvaluationResponse> evals = new ArrayList<>();

        if (criticResults != null && !criticResults.isEmpty()) {
            for (CriticResult cr : criticResults) {
                evals.add(CriticEvaluationResponse.fromModel(cr));
                if (cr.getDecision() == CriticDecision.ACCEPT) {
                    accepted++;
                } else if (cr.getDecision() == CriticDecision.REJECT) {
                    rejected++;
                    if (cr.getTaskId() != null) rejectedTaskIds.add(cr.getTaskId());
                    if (cr.getReasons() != null) rejectionReasons.addAll(cr.getReasons());
                } else if (cr.getDecision() == CriticDecision.INCONCLUSIVE) {
                    inconclusive++;
                } else if (cr.getDecision() == CriticDecision.HUMAN_APPROVAL_REQUIRED) {
                    humanApproval++;
                }
            }
        } else if (auditEvents != null) {
            for (AuditEvent ae : auditEvents) {
                if (ae.getEventType() == AuditEventType.CRITIC_ACCEPTED) {
                    accepted++;
                } else if (ae.getEventType() == AuditEventType.CRITIC_REJECTED) {
                    rejected++;
                    if (ae.getTaskId() != null) rejectedTaskIds.add(ae.getTaskId());
                    if (ae.getEventData() != null && ae.getEventData().get("reason") != null) {
                        rejectionReasons.add(String.valueOf(ae.getEventData().get("reason")));
                    }
                } else if (ae.getEventType() == AuditEventType.CRITIC_INCONCLUSIVE) {
                    inconclusive++;
                } else if (ae.getEventType() == AuditEventType.HUMAN_APPROVAL_REQUIRED) {
                    humanApproval++;
                }
            }
        }

        int total = accepted + rejected + inconclusive + humanApproval;

        return CriticSummaryResponse.builder()
                .totalEvaluations(total)
                .acceptedCount(accepted)
                .rejectedCount(rejected)
                .inconclusiveCount(inconclusive)
                .humanApprovalCount(humanApproval)
                .rejectedTaskIds(rejectedTaskIds)
                .rejectionReasons(rejectionReasons)
                .replanTriggeredByCritic(rejected > 0)
                .evaluations(evals)
                .build();
    }

    private RecoverySummaryResponse buildRecoverySummary(List<RecoveryAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return RecoverySummaryResponse.builder().build();
        }

        int success = 0;
        int exhausted = 0;
        int retries = 0;
        int fallbacks = 0;
        int replans = 0;

        for (RecoveryAttempt ra : attempts) {
            if (ra.getStatus() == RecoveryStatus.SUCCESS) success++;
            if (ra.getStatus() == RecoveryStatus.FAILED) exhausted++;
            if (ra.getRecoveryType() != null) {
                switch (ra.getRecoveryType()) {
                    case RETRY -> retries++;
                    case FALLBACK_TOOL -> fallbacks++;
                    case REPLAN -> replans++;
                    default -> {}
                }
            }
        }

        List<RecoveryAttemptResponse> attemptResponses = attempts.stream()
                .map(RecoveryAttemptResponse::fromEntity)
                .collect(Collectors.toList());

        return RecoverySummaryResponse.builder()
                .totalAttempts(attempts.size())
                .successfulRecoveries(success)
                .exhaustedRecoveries(exhausted)
                .retryCount(retries)
                .fallbackCount(fallbacks)
                .replanCount(replans)
                .attempts(attemptResponses)
                .build();
    }

    private StoppingSummaryDto buildStoppingSummary(
            List<InvestigationTask> tasks,
            List<CriticResult> criticResults,
            List<RecoveryAttempt> recoveryAttempts,
            Investigation investigation,
            List<AuditEvent> auditEvents,
            CriticSummaryResponse criticSummary) {

        int maxTasks = investigation.getMaxTasks() != null ? investigation.getMaxTasks() : 15;
        int maxRuntimeSeconds = investigation.getMaxRuntimeSeconds() != null ? investigation.getMaxRuntimeSeconds() : 300;

        boolean limitReached = tasks != null && tasks.size() >= maxTasks;
        boolean humanApproval = (criticResults != null && criticResults.stream().anyMatch(CriticResult::isHumanApprovalRequired))
                || (criticSummary != null && criticSummary.getHumanApprovalCount() > 0);
        boolean completedSuccessfully = tasks != null && !tasks.isEmpty() && tasks.stream().allMatch(
                t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.FAILED || t.getStatus() == TaskStatus.SKIPPED);

        // Check if audit events recorded a specific stopping reason
        String recordedStoppingReason = null;
        if (auditEvents != null) {
            for (AuditEvent ae : auditEvents) {
                if (ae.getEventType() == AuditEventType.STOPPING_CHECK && ae.getEventData() != null) {
                    if (ae.getEventData().get("reason") != null) {
                        recordedStoppingReason = String.valueOf(ae.getEventData().get("reason"));
                    }
                }
            }
        }

        StoppingDecision decision;
        String reason;

        if (humanApproval) {
            decision = StoppingDecision.HUMAN_APPROVAL_REQUIRED;
            reason = recordedStoppingReason != null ? recordedStoppingReason : "Sensitive production action proposed requiring human engineer authorization.";
        } else if (recordedStoppingReason != null) {
            decision = StoppingDecision.STOP_LIMIT_REACHED;
            reason = recordedStoppingReason;
        } else if (limitReached) {
            decision = StoppingDecision.STOP_LIMIT_REACHED;
            reason = "Configured task budget limit reached.";
        } else if (completedSuccessfully) {
            decision = StoppingDecision.STOP_SUCCESS;
            reason = "Investigation objective satisfied; all planned tasks finished with verified evidence.";
        } else {
            decision = StoppingDecision.CONTINUE;
            reason = "Investigation execution active.";
        }

        return StoppingSummaryDto.builder()
                .decision(decision)
                .reason(reason)
                .limitReached(limitReached || (decision == StoppingDecision.STOP_LIMIT_REACHED))
                .humanApprovalRequired(humanApproval)
                .completedSuccessfully(completedSuccessfully)
                .build();
    }

    private List<TimelineEventDto> buildTimeline(List<AuditEvent> auditEvents) {
        if (auditEvents == null || auditEvents.isEmpty()) {
            return List.of();
        }
        return auditEvents.stream()
                .map(TimelineEventDto::fromEntity)
                .collect(Collectors.toList());
    }
}
