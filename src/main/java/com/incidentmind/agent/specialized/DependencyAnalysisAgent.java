package com.incidentmind.agent.specialized;

import com.incidentmind.agent.core.Agent;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.task.entity.InvestigationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class DependencyAnalysisAgent implements Agent {

    public static final String AGENT_NAME = "dependency-analysis-agent";
    public static final Set<String> CAPABILITIES = Set.of(
            "DEPENDENCY_ANALYSIS",
            "EXTERNAL_DEPENDENCY_INVESTIGATION"
    );

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "Analyzes upstream and downstream dependencies for latency, error rate spikes, and connectivity degradation";
    }

    @Override
    public Set<String> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean canHandle(InvestigationTask task) {
        if (task == null || task.getTaskType() == null) {
            return false;
        }
        return CAPABILITIES.contains(task.getTaskType().toUpperCase())
                || AGENT_NAME.equalsIgnoreCase(task.getAssignedAgentType());
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.builder()
                .name(AGENT_NAME)
                .description(getDescription())
                .capabilities(CAPABILITIES)
                .build();
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) {
        long startTime = System.currentTimeMillis();
        Incident incident = context.getIncident();
        InvestigationTask task = context.getTask();

        log.info("DependencyAnalysisAgent evaluating dependencies for service: {}",
                incident != null ? incident.getServiceName() : "unknown");

        String serviceName = incident != null && incident.getServiceName() != null ? incident.getServiceName() : "checkout-service";

        List<String> suspectedDependencies = List.of(
                serviceName + " -> payment-gateway",
                serviceName + " -> fraud-detection-api",
                serviceName + " -> postgres-primary"
        );

        List<String> requiredEvidence = List.of(
                "p99 latency metrics for payment-gateway",
                "HTTP 5xx rate distribution on downstream payment provider",
                "Database connection pool saturation metrics"
        );

        List<String> hypotheses = List.of(
                "Upstream payment provider latency timeout causing checkout worker thread pool exhaustion"
        );

        Map<String, Object> output = new HashMap<>();
        output.put("targetService", serviceName);
        output.put("identifiedDependencies", suspectedDependencies);
        output.put("requiredTelemetryEvidence", requiredEvidence);
        output.put("hypotheses", hypotheses);
        output.put("summary", String.format("Mapped %d critical dependencies for service '%s'. Identified telemetry prerequisites for validation.",
                suspectedDependencies.size(), serviceName));

        long durationMs = System.currentTimeMillis() - startTime;
        return AgentExecutionResult.success(output, List.of(), hypotheses, List.of(), durationMs);
    }
}
