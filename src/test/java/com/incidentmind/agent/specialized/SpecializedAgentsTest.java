package com.incidentmind.agent.specialized;

import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecializedAgentsTest {

    @Mock
    private ToolGateway toolGateway;

    @Test
    @DisplayName("IncidentTriageAgent: produces structured hypotheses distinguishing observation from hypothesis")
    void triageAgent_ProducesHypotheses() {
        IncidentTriageAgent agent = new IncidentTriageAgent();

        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .incidentKey("INC-000001")
                .title("Checkout API error rate increased")
                .description("Checkout error rate spiked from 2% to 18% shortly after deployment v2.4.1")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.INVESTIGATING)
                .serviceName("checkout-service")
                .environment("production")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("INCIDENT_TRIAGE")
                .title("Triage reported issue")
                .build();

        AgentExecutionContext context = AgentExecutionContext.builder()
                .incident(incident)
                .task(task)
                .build();

        AgentExecutionResult result = agent.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.getHypotheses()).isNotEmpty();
        assertThat(result.getHypotheses().get(0)).contains("DEPLOYMENT_REGRESSION");
        assertThat(result.getRecommendedTaskTypes()).contains("CHANGE_ANALYSIS");
    }

    @Test
    @DisplayName("ChangeAnalysisAgent: invokes ToolGateway and produces factual evidence from GitHub tool response")
    void changeAnalysisAgent_SuccessCreatesEvidence() {
        ChangeAnalysisAgent agent = new ChangeAnalysisAgent();

        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .serviceName("checkout-service")
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("CHANGE_ANALYSIS")
                .build();

        UUID toolCallId = UUID.randomUUID();
        Map<String, Object> commitItem = Map.of(
                "sha", "abc1234567890",
                "commit", Map.of("message", "Fix payment checkout deadlock")
        );

        ToolResult toolResult = ToolResult.success(
                "github.get_recent_commits",
                "EXTERNAL_API",
                200,
                Map.of("items", List.of(commitItem), "count", 1),
                Map.of("X-RateLimit-Remaining", "58"),
                95L
        );
        toolResult.setToolCallId(toolCallId);

        when(toolGateway.invokeTool(any(ToolRequest.class))).thenReturn(toolResult);

        AgentExecutionContext context = AgentExecutionContext.builder()
                .incident(incident)
                .task(task)
                .toolGateway(toolGateway)
                .inputData(Map.of("owner", "octocat", "repository", "Hello-World"))
                .build();

        AgentExecutionResult result = agent.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.getEvidenceDrafts()).hasSize(1);
        assertThat(result.getEvidenceDrafts().get(0).getSourceType()).isEqualTo("GITHUB");
        assertThat(result.getEvidenceDrafts().get(0).getSourceReference()).contains("commit:abc1234567890");
        assertThat(result.getEvidenceDrafts().get(0).getConfidence()).isEqualTo(new BigDecimal("0.9500"));

        verify(toolGateway).invokeTool(any(ToolRequest.class));
    }

    @Test
    @DisplayName("ChangeAnalysisAgent: handles ToolGateway failure gracefully with controlled failure state")
    void changeAnalysisAgent_ToolFailure_ReturnsControlledFailure() {
        ChangeAnalysisAgent agent = new ChangeAnalysisAgent();

        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .serviceName("checkout-service")
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("CHANGE_ANALYSIS")
                .build();

        ToolResult toolResult = ToolResult.failure(
                "github.get_recent_commits",
                "EXTERNAL_API",
                ToolCallStatus.FAILED,
                500,
                "GITHUB_INTERNAL_ERROR",
                "Internal Server Error on GitHub API",
                ErrorClassification.HTTP_5XX,
                Map.of("error", "Internal Server Error"),
                Map.of(),
                120L
        );

        when(toolGateway.invokeTool(any(ToolRequest.class))).thenReturn(toolResult);

        AgentExecutionContext context = AgentExecutionContext.builder()
                .incident(incident)
                .task(task)
                .toolGateway(toolGateway)
                .build();

        AgentExecutionResult result = agent.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("GITHUB_INTERNAL_ERROR");
        assertThat(result.getErrorMessage()).contains("Internal Server Error");
    }

    @Test
    @DisplayName("DependencyAnalysisAgent: analyzes dependencies and formulates required telemetry prerequisites")
    void dependencyAnalysisAgent_FormulatesTelemetryRequirements() {
        DependencyAnalysisAgent agent = new DependencyAnalysisAgent();

        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .serviceName("checkout-service")
                .build();

        InvestigationTask task = InvestigationTask.builder()
                .id(UUID.randomUUID())
                .taskType("DEPENDENCY_ANALYSIS")
                .build();

        AgentExecutionContext context = AgentExecutionContext.builder()
                .incident(incident)
                .task(task)
                .build();

        AgentExecutionResult result = agent.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.getOutputPayload().get("identifiedDependencies")).isNotNull();
        assertThat(result.getOutputPayload().get("requiredTelemetryEvidence")).isNotNull();
    }
}
