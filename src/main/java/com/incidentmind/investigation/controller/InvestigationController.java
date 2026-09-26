package com.incidentmind.investigation.controller;

import com.incidentmind.investigation.dto.AuditEventResponse;
import com.incidentmind.investigation.dto.CreateInvestigationRequest;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.dto.TaskResponse;
import com.incidentmind.investigation.service.InvestigationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class InvestigationController {

    private final InvestigationService investigationService;

    public InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @PostMapping("/api/v1/incidents/{incidentId}/investigations")
    public ResponseEntity<InvestigationResponse> createInvestigation(
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateInvestigationRequest request) {
        InvestigationResponse response = investigationService.createInvestigation(incidentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/incidents/{incidentId}/investigations")
    public ResponseEntity<List<InvestigationResponse>> getIncidentInvestigations(@PathVariable UUID incidentId) {
        List<InvestigationResponse> list = investigationService.getInvestigationsByIncidentId(incidentId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/api/v1/investigations/{id}")
    public ResponseEntity<InvestigationResponse> getInvestigation(@PathVariable UUID id) {
        InvestigationResponse response = investigationService.getInvestigationById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/investigations/{id}/tasks")
    public ResponseEntity<List<TaskResponse>> getInvestigationTasks(@PathVariable UUID id) {
        List<TaskResponse> tasks = investigationService.getInvestigationTasks(id);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/api/v1/investigations/{id}/evidence")
    public ResponseEntity<List<EvidenceResponse>> getInvestigationEvidence(@PathVariable UUID id) {
        List<EvidenceResponse> evidence = investigationService.getInvestigationEvidence(id);
        return ResponseEntity.ok(evidence);
    }

    @GetMapping("/api/v1/investigations/{id}/audit")
    public ResponseEntity<List<AuditEventResponse>> getInvestigationAuditTrail(@PathVariable UUID id) {
        List<AuditEventResponse> auditEvents = investigationService.getInvestigationAuditTrail(id);
        return ResponseEntity.ok(auditEvents);
    }

    @PostMapping("/api/v1/investigations/{id}/start")
    public ResponseEntity<InvestigationResponse> startInvestigation(@PathVariable UUID id) {
        InvestigationResponse response = investigationService.startInvestigation(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/investigations/{id}/agent-runs")
    public ResponseEntity<List<com.incidentmind.agent.dto.AgentRunResponse>> getInvestigationAgentRuns(@PathVariable UUID id) {
        List<com.incidentmind.agent.dto.AgentRunResponse> agentRuns = investigationService.getInvestigationAgentRuns(id);
        return ResponseEntity.ok(agentRuns);
    }

    @GetMapping("/api/v1/investigations/{id}/recovery-attempts")
    public ResponseEntity<List<com.incidentmind.recovery.dto.RecoveryAttemptResponse>> getInvestigationRecoveryAttempts(@PathVariable UUID id) {
        List<com.incidentmind.recovery.dto.RecoveryAttemptResponse> attempts = investigationService.getInvestigationRecoveryAttempts(id);
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/api/v1/investigations/{id}/metrics")
    public ResponseEntity<com.incidentmind.blackboard.dto.InvestigationMetricsResponse> getInvestigationMetrics(@PathVariable UUID id) {
        com.incidentmind.blackboard.dto.InvestigationMetricsResponse metrics = investigationService.getInvestigationMetrics(id);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/api/v1/investigations/{id}/critic-evaluations")
    public ResponseEntity<List<com.incidentmind.critic.dto.CriticEvaluationResponse>> getInvestigationCriticEvaluations(@PathVariable UUID id) {
        List<com.incidentmind.critic.dto.CriticEvaluationResponse> evaluations = investigationService.getInvestigationCriticEvaluations(id);
        return ResponseEntity.ok(evaluations);
    }

    @GetMapping("/api/v1/investigations/{id}/report")
    public ResponseEntity<com.incidentmind.report.dto.InvestigationReportResponse> getInvestigationReport(@PathVariable UUID id) {
        com.incidentmind.report.dto.InvestigationReportResponse report = investigationService.getInvestigationReport(id);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/api/v1/investigations/{id}/timeline")
    public ResponseEntity<List<com.incidentmind.report.dto.TimelineEventDto>> getInvestigationTimeline(@PathVariable UUID id) {
        List<com.incidentmind.report.dto.TimelineEventDto> timeline = investigationService.getInvestigationTimeline(id);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/api/v1/investigations/{id}/graph")
    public ResponseEntity<com.incidentmind.report.dto.TaskGraphResponse> getInvestigationGraph(@PathVariable UUID id) {
        com.incidentmind.report.dto.TaskGraphResponse graph = investigationService.getInvestigationGraph(id);
        return ResponseEntity.ok(graph);
    }

    @GetMapping("/api/v1/investigations/{id}/agents")
    public ResponseEntity<List<com.incidentmind.report.dto.AgentActivityResponse>> getInvestigationAgents(@PathVariable UUID id) {
        List<com.incidentmind.report.dto.AgentActivityResponse> agents = investigationService.getInvestigationAgents(id);
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/api/v1/investigations/{id}/tools")
    public ResponseEntity<List<com.incidentmind.report.dto.ToolExecutionResponse>> getInvestigationTools(@PathVariable UUID id) {
        List<com.incidentmind.report.dto.ToolExecutionResponse> tools = investigationService.getInvestigationTools(id);
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/api/v1/investigations/{id}/recovery")
    public ResponseEntity<com.incidentmind.report.dto.RecoverySummaryResponse> getInvestigationRecoverySummary(@PathVariable UUID id) {
        com.incidentmind.report.dto.RecoverySummaryResponse recovery = investigationService.getInvestigationRecoverySummary(id);
        return ResponseEntity.ok(recovery);
    }

    @GetMapping("/api/v1/investigations/{id}/critic-summary")
    public ResponseEntity<com.incidentmind.report.dto.CriticSummaryResponse> getInvestigationCriticSummary(@PathVariable UUID id) {
        com.incidentmind.report.dto.CriticSummaryResponse criticSummary = investigationService.getInvestigationCriticSummary(id);
        return ResponseEntity.ok(criticSummary);
    }

    @PostMapping("/api/v1/investigations/{id}/human-actions")
    public ResponseEntity<com.incidentmind.investigation.dto.HumanActionResponse> submitHumanAction(
            @PathVariable UUID id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.incidentmind.investigation.dto.HumanActionRequest request) {
        com.incidentmind.investigation.dto.HumanActionResponse response = investigationService.processHumanAction(id, request);
        return ResponseEntity.ok(response);
    }
}
