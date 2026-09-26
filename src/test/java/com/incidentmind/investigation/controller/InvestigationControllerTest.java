package com.incidentmind.investigation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.common.exception.GlobalExceptionHandler;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.common.filter.CorrelationIdFilter;
import com.incidentmind.investigation.dto.AuditEventResponse;
import com.incidentmind.investigation.dto.CreateInvestigationRequest;
import com.incidentmind.investigation.dto.EvidenceResponse;
import com.incidentmind.investigation.dto.InvestigationResponse;
import com.incidentmind.investigation.dto.TaskResponse;
import com.incidentmind.investigation.entity.InvestigationStatus;
import com.incidentmind.investigation.service.InvestigationService;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvestigationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InvestigationService investigationService;

    @InjectMocks
    private InvestigationController investigationController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(investigationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    @DisplayName("4. Create investigation for existing incident - returns 201 Created")
    void createInvestigation_Success() throws Exception {
        UUID incidentId = UUID.randomUUID();
        UUID investigationId = UUID.randomUUID();
        CreateInvestigationRequest request = CreateInvestigationRequest.builder()
                .objective("Determine root cause of checkout API degradation")
                .maxTasks(12)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .build();

        InvestigationResponse response = InvestigationResponse.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .objective(request.getObjective())
                .maxTasks(12)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(120)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(investigationService.createInvestigation(eq(incidentId), any(CreateInvestigationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/incidents/{incidentId}/investigations", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(investigationId.toString())))
                .andExpect(jsonPath("$.incidentId", is(incidentId.toString())))
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.maxTasks", is(12)))
                .andExpect(jsonPath("$.maxRetriesPerTask", is(2)))
                .andExpect(jsonPath("$.maxRuntimeSeconds", is(120)));

        verify(investigationService).createInvestigation(eq(incidentId), any(CreateInvestigationRequest.class));
    }

    @Test
    @DisplayName("5. Reject investigation for nonexistent incident - returns 404 Not Found")
    void createInvestigation_NonexistentIncident_Returns404() throws Exception {
        UUID nonexistentIncidentId = UUID.randomUUID();
        CreateInvestigationRequest request = CreateInvestigationRequest.builder()
                .objective("Determine root cause")
                .maxTasks(10)
                .maxRetriesPerTask(2)
                .maxRuntimeSeconds(60)
                .build();

        when(investigationService.createInvestigation(eq(nonexistentIncidentId), any(CreateInvestigationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Incident", nonexistentIncidentId));

        mockMvc.perform(post("/api/v1/incidents/{incidentId}/investigations", nonexistentIncidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString(nonexistentIncidentId.toString())));
    }

    @Test
    @DisplayName("6. Retrieve investigation - returns 200 OK")
    void getInvestigation_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        InvestigationResponse response = InvestigationResponse.builder()
                .id(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.CREATED)
                .objective("Investigate latency spike")
                .maxTasks(8)
                .maxRetriesPerTask(1)
                .maxRuntimeSeconds(90)
                .build();

        when(investigationService.getInvestigationById(investigationId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/investigations/{id}", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(investigationId.toString())))
                .andExpect(jsonPath("$.objective", is("Investigate latency spike")));
    }

    @Test
    @DisplayName("7. Retrieve tasks - returns list of tasks")
    void getInvestigationTasks_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        TaskResponse task1 = TaskResponse.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskType("METRIC_ANALYSIS")
                .title("Analyze error rate metrics")
                .description("Check Datadog 5xx error rate metrics")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.PENDING)
                .attemptCount(0)
                .maxAttempts(3)
                .build();

        when(investigationService.getInvestigationTasks(investigationId)).thenReturn(List.of(task1));

        mockMvc.perform(get("/api/v1/investigations/{id}/tasks", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskType", is("METRIC_ANALYSIS")))
                .andExpect(jsonPath("$[0].title", is("Analyze error rate metrics")));
    }

    @Test
    @DisplayName("8. Retrieve evidence - returns list of evidence")
    void getInvestigationEvidence_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        EvidenceResponse ev = EvidenceResponse.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .sourceType("METRICS")
                .sourceReference("datadog:metric:checkout.5xx.rate")
                .claim("Checkout 5xx rate jumped to 18% post deployment v2.4.1")
                .confidence(new BigDecimal("0.9800"))
                .rawData(Map.of("errorRate", 0.18, "threshold", 0.02))
                .collectedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(investigationService.getInvestigationEvidence(investigationId)).thenReturn(List.of(ev));

        mockMvc.perform(get("/api/v1/investigations/{id}/evidence", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sourceType", is("METRICS")))
                .andExpect(jsonPath("$[0].claim", is("Checkout 5xx rate jumped to 18% post deployment v2.4.1")));
    }

    @Test
    @DisplayName("9. Retrieve audit events in chronological order - returns 200 OK")
    void getInvestigationAuditTrail_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-09-26T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-26T10:01:00Z");

        AuditEventResponse event1 = AuditEventResponse.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.INVESTIGATION_CREATED)
                .actorType(ActorType.USER)
                .occurredAt(t1)
                .build();

        AuditEventResponse event2 = AuditEventResponse.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .eventType(AuditEventType.TASK_CREATED)
                .actorType(ActorType.SYSTEM)
                .occurredAt(t2)
                .build();

        when(investigationService.getInvestigationAuditTrail(investigationId)).thenReturn(List.of(event1, event2));

        mockMvc.perform(get("/api/v1/investigations/{id}/audit", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventType", is("INVESTIGATION_CREATED")))
                .andExpect(jsonPath("$[1].eventType", is("TASK_CREATED")));
    }

    @Test
    @DisplayName("POST /api/v1/investigations/{id}/start starts investigation and returns updated metadata")
    void startInvestigation_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        InvestigationResponse response = InvestigationResponse.builder()
                .id(investigationId)
                .status(InvestigationStatus.WAITING)
                .objective("Determine root cause of checkout latency")
                .build();

        when(investigationService.startInvestigation(investigationId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/investigations/{id}/start", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(investigationId.toString())))
                .andExpect(jsonPath("$.status", is("WAITING")));

        verify(investigationService).startInvestigation(investigationId);
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/agent-runs returns agent execution history")
    void getInvestigationAgentRuns_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.agent.dto.AgentRunResponse run1 = com.incidentmind.agent.dto.AgentRunResponse.builder()
                .id(UUID.randomUUID())
                .agentType("incident-triage-agent")
                .status(com.incidentmind.agent.entity.AgentRunStatus.COMPLETED)
                .durationMs(120L)
                .build();

        when(investigationService.getInvestigationAgentRuns(investigationId)).thenReturn(List.of(run1));

        mockMvc.perform(get("/api/v1/investigations/{id}/agent-runs", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].agentType", is("incident-triage-agent")))
                .andExpect(jsonPath("$[0].status", is("COMPLETED")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/recovery-attempts returns recovery attempts history")
    void getInvestigationRecoveryAttempts_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.recovery.dto.RecoveryAttemptResponse attempt1 = com.incidentmind.recovery.dto.RecoveryAttemptResponse.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .recoveryType(com.incidentmind.recovery.entity.RecoveryType.RETRY)
                .status(com.incidentmind.recovery.entity.RecoveryStatus.SUCCESS)
                .reason("Transient 503 error")
                .attemptNumber(1)
                .build();

        when(investigationService.getInvestigationRecoveryAttempts(investigationId)).thenReturn(List.of(attempt1));

        mockMvc.perform(get("/api/v1/investigations/{id}/recovery-attempts", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recoveryType", is("RETRY")))
                .andExpect(jsonPath("$[0].status", is("SUCCESS")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/metrics returns blackboard execution metrics")
    void getInvestigationMetrics_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.blackboard.dto.InvestigationMetricsResponse metrics = com.incidentmind.blackboard.dto.InvestigationMetricsResponse.builder()
                .tasksCreatedCount(3)
                .tasksCompletedCount(2)
                .tasksFailedCount(1)
                .tasksRejectedCount(1)
                .criticEvaluationsCount(2)
                .criticAcceptancesCount(1)
                .criticRejectionsCount(1)
                .agentRunsCount(3)
                .toolCallsCount(2)
                .recoveryAttemptsCount(1)
                .build();

        when(investigationService.getInvestigationMetrics(investigationId)).thenReturn(metrics);

        mockMvc.perform(get("/api/v1/investigations/{id}/metrics", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksCreatedCount", is(3)))
                .andExpect(jsonPath("$.tasksCompletedCount", is(2)))
                .andExpect(jsonPath("$.tasksRejectedCount", is(1)))
                .andExpect(jsonPath("$.criticAcceptancesCount", is(1)))
                .andExpect(jsonPath("$.criticRejectionsCount", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/critic-evaluations returns critic decisions")
    void getInvestigationCriticEvaluations_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.critic.dto.CriticEvaluationResponse eval = com.incidentmind.critic.dto.CriticEvaluationResponse.builder()
                .decision(com.incidentmind.critic.model.CriticDecision.REJECT)
                .confidence(0.40)
                .reasons(List.of("Unsupported causal claim"))
                .failedChecks(List.of("UNSUPPORTED_CAUSAL_CLAIM"))
                .recommendedNextAction("INVESTIGATE_PULL_REQUESTS")
                .build();

        when(investigationService.getInvestigationCriticEvaluations(investigationId)).thenReturn(List.of(eval));

        mockMvc.perform(get("/api/v1/investigations/{id}/critic-evaluations", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].decision", is("REJECT")))
                .andExpect(jsonPath("$[0].recommendedNextAction", is("INVESTIGATE_PULL_REQUESTS")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/report returns structured final report")
    void getInvestigationReport_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        com.incidentmind.report.dto.InvestigationReportResponse report = com.incidentmind.report.dto.InvestigationReportResponse.builder()
                .investigationId(investigationId)
                .incidentId(incidentId)
                .status(InvestigationStatus.COMPLETED)
                .finalConclusion("Evidence supports recent deployment correlation.")
                .conclusionType(com.incidentmind.report.model.ConclusionType.SUPPORTED_FINDING)
                .confidenceReasoning("Grounding score 0.85 across 2 verified findings.")
                .keyFindings(List.of("Recent commit sha123 occurred 8m before incident."))
                .hypotheses(List.of("Deployment may have introduced regression."))
                .unresolvedQuestions(List.of("Repository evidence alone cannot establish causality."))
                .recommendedNextActions(List.of("Review PR diff #42"))
                .stopping(com.incidentmind.report.dto.StoppingSummaryDto.builder().reason("TASK_BUDGET_REACHED").humanApprovalRequired(false).build())
                .generatedAt(Instant.now())
                .build();

        when(investigationService.getInvestigationReport(investigationId)).thenReturn(report);

        mockMvc.perform(get("/api/v1/investigations/{id}/report", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investigationId", is(investigationId.toString())))
                .andExpect(jsonPath("$.incidentId", is(incidentId.toString())))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.conclusionType", is("SUPPORTED_FINDING")))
                .andExpect(jsonPath("$.finalConclusion", is("Evidence supports recent deployment correlation.")))
                .andExpect(jsonPath("$.stopping.reason", is("TASK_BUDGET_REACHED")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/timeline returns investigation lifecycle timeline")
    void getInvestigationTimeline_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.report.dto.TimelineEventDto event = com.incidentmind.report.dto.TimelineEventDto.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .eventType(AuditEventType.INVESTIGATION_STARTED)
                .actorType(ActorType.ORCHESTRATOR)
                .description("Investigation started and initialized")
                .build();

        when(investigationService.getInvestigationTimeline(investigationId)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/investigations/{id}/timeline", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType", is("INVESTIGATION_STARTED")))
                .andExpect(jsonPath("$[0].description", is("Investigation started and initialized")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/graph returns dynamic task graph")
    void getInvestigationGraph_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        com.incidentmind.report.dto.TaskGraphNode node = com.incidentmind.report.dto.TaskGraphNode.builder()
                .taskId(taskId)
                .taskType("CHANGE_ANALYSIS")
                .title("Analyze recent GitHub commits")
                .assignedAgent("change-analysis-agent")
                .status(TaskStatus.COMPLETED)
                .priority(TaskPriority.HIGH)
                .criticDecision(com.incidentmind.critic.model.CriticDecision.ACCEPT)
                .retryCount(0)
                .build();

        com.incidentmind.report.dto.TaskGraphResponse graph = com.incidentmind.report.dto.TaskGraphResponse.builder()
                .investigationId(investigationId)
                .nodes(List.of(node))
                .edges(List.of())
                .totalNodes(1)
                .totalEdges(0)
                .build();

        when(investigationService.getInvestigationGraph(investigationId)).thenReturn(graph);

        mockMvc.perform(get("/api/v1/investigations/{id}/graph", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investigationId", is(investigationId.toString())))
                .andExpect(jsonPath("$.nodes", hasSize(1)))
                .andExpect(jsonPath("$.nodes[0].taskType", is("CHANGE_ANALYSIS")))
                .andExpect(jsonPath("$.nodes[0].assignedAgent", is("change-analysis-agent")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/agents returns agent activity")
    void getInvestigationAgents_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.report.dto.AgentActivityResponse agent = com.incidentmind.report.dto.AgentActivityResponse.builder()
                .agentRunId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .agentType("incident-triage-agent")
                .status(com.incidentmind.agent.entity.AgentRunStatus.COMPLETED)
                .durationMs(250L)
                .evidenceProduced(1)
                .criticDecision(com.incidentmind.critic.model.CriticDecision.ACCEPT)
                .build();

        when(investigationService.getInvestigationAgents(investigationId)).thenReturn(List.of(agent));

        mockMvc.perform(get("/api/v1/investigations/{id}/agents", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].agentType", is("incident-triage-agent")))
                .andExpect(jsonPath("$[0].criticDecision", is("ACCEPT")));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/tools returns tool executions without secrets")
    void getInvestigationTools_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.report.dto.ToolExecutionResponse tool = com.incidentmind.report.dto.ToolExecutionResponse.builder()
                .toolCallId(UUID.randomUUID())
                .toolName("github_get_commit")
                .toolType("REST_API")
                .status(com.incidentmind.tool.entity.ToolCallStatus.SUCCESS)
                .httpStatus(200)
                .durationMs(110L)
                .build();

        when(investigationService.getInvestigationTools(investigationId)).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/v1/investigations/{id}/tools", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].toolName", is("github_get_commit")))
                .andExpect(jsonPath("$[0].httpStatus", is(200)));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/recovery returns recovery summary")
    void getInvestigationRecovery_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.report.dto.RecoverySummaryResponse recovery = com.incidentmind.report.dto.RecoverySummaryResponse.builder()
                .totalAttempts(2)
                .successfulRecoveries(1)
                .exhaustedRecoveries(1)
                .retryCount(2)
                .fallbackCount(0)
                .replanCount(0)
                .build();

        when(investigationService.getInvestigationRecoverySummary(investigationId)).thenReturn(recovery);

        mockMvc.perform(get("/api/v1/investigations/{id}/recovery", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts", is(2)))
                .andExpect(jsonPath("$.successfulRecoveries", is(1)))
                .andExpect(jsonPath("$.exhaustedRecoveries", is(1)))
                .andExpect(jsonPath("$.retryCount", is(2)));
    }

    @Test
    @DisplayName("GET /api/v1/investigations/{id}/critic-summary returns aggregated critic statistics")
    void getInvestigationCriticSummary_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.report.dto.CriticSummaryResponse criticSummary = com.incidentmind.report.dto.CriticSummaryResponse.builder()
                .totalEvaluations(3)
                .acceptedCount(2)
                .rejectedCount(1)
                .inconclusiveCount(0)
                .humanApprovalCount(0)
                .rejectedTaskIds(List.of(UUID.randomUUID()))
                .rejectionReasons(List.of("Unsupported causal claim"))
                .replanTriggeredByCritic(true)
                .build();

        when(investigationService.getInvestigationCriticSummary(investigationId)).thenReturn(criticSummary);

        mockMvc.perform(get("/api/v1/investigations/{id}/critic-summary", investigationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvaluations", is(3)))
                .andExpect(jsonPath("$.acceptedCount", is(2)))
                .andExpect(jsonPath("$.rejectedCount", is(1)))
                .andExpect(jsonPath("$.replanTriggeredByCritic", is(true)));
    }

    @Test
    @DisplayName("POST /api/v1/investigations/{id}/human-actions returns processed action response")
    void postHumanAction_Success() throws Exception {
        UUID investigationId = UUID.randomUUID();
        com.incidentmind.investigation.dto.HumanActionRequest request = com.incidentmind.investigation.dto.HumanActionRequest.builder()
                .action("APPROVE_ACTION")
                .notes("Approved rollback for test incident")
                .build();

        com.incidentmind.investigation.dto.HumanActionResponse response = com.incidentmind.investigation.dto.HumanActionResponse.builder()
                .investigationId(investigationId)
                .action("APPROVE_ACTION")
                .status("PROCESSED")
                .message("Human action 'APPROVE_ACTION' successfully recorded and applied.")
                .build();

        when(investigationService.processHumanAction(eq(investigationId), any(com.incidentmind.investigation.dto.HumanActionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/investigations/{id}/human-actions", investigationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action", is("APPROVE_ACTION")))
                .andExpect(jsonPath("$.status", is("PROCESSED")));
    }
}

