package com.incidentmind.agent.specialized;

import com.incidentmind.agent.core.Agent;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.task.entity.InvestigationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class IncidentTriageAgent implements Agent {

    public static final String AGENT_NAME = "incident-triage-agent";
    public static final Set<String> CAPABILITIES = Set.of("INCIDENT_TRIAGE", "TRIAGE");

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "Performs initial incident triage, identifies failure patterns, and produces investigation hypotheses";
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
        String description = incident != null && incident.getDescription() != null ? incident.getDescription().toLowerCase() : "";
        String title = incident != null && incident.getTitle() != null ? incident.getTitle().toLowerCase() : "";

        log.info("IncidentTriageAgent evaluating incident: id={}, title='{}'",
                incident != null ? incident.getId() : "null", incident != null ? incident.getTitle() : "null");

        List<String> hypotheses = new ArrayList<>();
        List<String> recommendedTasks = new ArrayList<>();
        Map<String, Object> output = new HashMap<>();

        boolean mentionsDeployment = description.contains("deploy") || description.contains("release")
                || description.contains("version") || title.contains("deploy") || title.contains("release");

        boolean mentionsDependency = description.contains("gateway") || description.contains("timeout")
                || description.contains("upstream") || description.contains("database") || description.contains("dependency");

        if (mentionsDeployment) {
            hypotheses.add("DEPLOYMENT_REGRESSION: Incident timing or symptoms correlate with a recent software release or deployment");
            recommendedTasks.add("CHANGE_ANALYSIS");
        }

        if (mentionsDependency || !mentionsDeployment) {
            hypotheses.add("DEPENDENCY_FAILURE: Upstream/downstream service latency or third-party dependency failure");
            recommendedTasks.add("DEPENDENCY_ANALYSIS");
        }

        if (hypotheses.isEmpty()) {
            hypotheses.add("UNSPECIFIED_ANOMALY: Systematic degradation requiring broad metric and change inspection");
            recommendedTasks.add("CHANGE_ANALYSIS");
            recommendedTasks.add("DEPENDENCY_ANALYSIS");
        }

        String summary = String.format("Triage analysis completed for service '%s' in '%s' environment. Identified %d initial hypothesis/hypotheses.",
                incident != null ? incident.getServiceName() : "unknown",
                incident != null ? incident.getEnvironment() : "unknown",
                hypotheses.size());

        output.put("summary", summary);
        output.put("serviceName", incident != null ? incident.getServiceName() : "unknown");
        output.put("environment", incident != null ? incident.getEnvironment() : "unknown");
        output.put("severity", incident != null && incident.getSeverity() != null ? incident.getSeverity().name() : "unknown");
        output.put("hypotheses", hypotheses);
        output.put("recommendedTasks", recommendedTasks);

        long durationMs = System.currentTimeMillis() - startTime;
        return AgentExecutionResult.success(output, List.of(), hypotheses, recommendedTasks, durationMs);
    }
}
