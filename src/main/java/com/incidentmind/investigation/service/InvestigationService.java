package com.incidentmind.investigation.service;

import com.incidentmind.investigation.dto.AuditEventResponse;
import com.incidentmind.investigation.dto.CreateInvestigationRequest;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.dto.TaskResponse;

import java.util.List;
import java.util.UUID;

public interface InvestigationService {

    InvestigationResponse createInvestigation(UUID incidentId, CreateInvestigationRequest request);

    List<InvestigationResponse> getInvestigationsByIncidentId(UUID incidentId);

    InvestigationResponse getInvestigationById(UUID id);

    List<TaskResponse> getInvestigationTasks(UUID investigationId);

    List<EvidenceResponse> getInvestigationEvidence(UUID investigationId);

    List<AuditEventResponse> getInvestigationAuditTrail(UUID investigationId);

    InvestigationResponse startInvestigation(UUID id);

    List<com.incidentmind.agent.dto.AgentRunResponse> getInvestigationAgentRuns(UUID investigationId);

    List<com.incidentmind.recovery.dto.RecoveryAttemptResponse> getInvestigationRecoveryAttempts(UUID investigationId);

    com.incidentmind.blackboard.dto.InvestigationMetricsResponse getInvestigationMetrics(UUID investigationId);

    List<com.incidentmind.critic.dto.CriticEvaluationResponse> getInvestigationCriticEvaluations(UUID investigationId);

    com.incidentmind.report.dto.InvestigationReportResponse getInvestigationReport(UUID investigationId);

    List<com.incidentmind.report.dto.TimelineEventDto> getInvestigationTimeline(UUID investigationId);

    com.incidentmind.report.dto.TaskGraphResponse getInvestigationGraph(UUID investigationId);

    List<com.incidentmind.report.dto.AgentActivityResponse> getInvestigationAgents(UUID investigationId);

    List<com.incidentmind.report.dto.ToolExecutionResponse> getInvestigationTools(UUID investigationId);

    com.incidentmind.report.dto.RecoverySummaryResponse getInvestigationRecoverySummary(UUID investigationId);

    com.incidentmind.report.dto.CriticSummaryResponse getInvestigationCriticSummary(UUID investigationId);
 
    com.incidentmind.investigation.dto.HumanActionResponse processHumanAction(UUID investigationId, com.incidentmind.investigation.dto.HumanActionRequest request);
}
