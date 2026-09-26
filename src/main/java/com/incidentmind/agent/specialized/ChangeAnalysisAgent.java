package com.incidentmind.agent.specialized;

import com.incidentmind.agent.core.Agent;
import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.recovery.model.RecoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class ChangeAnalysisAgent implements Agent {

    public static final String AGENT_NAME = "change-analysis-agent";
    public static final Set<String> CAPABILITIES = Set.of(
            "CHANGE_ANALYSIS",
            "INVESTIGATE_RECENT_COMMITS",
            "INVESTIGATE_PULL_REQUESTS"
    );

    private final com.incidentmind.recovery.core.RecoveryEngine recoveryEngine;

    public ChangeAnalysisAgent() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChangeAnalysisAgent(@org.springframework.beans.factory.annotation.Autowired(required = false)
                                com.incidentmind.recovery.core.RecoveryEngine recoveryEngine) {
        this.recoveryEngine = recoveryEngine;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "Investigates code, commit, and pull request changes via GitHub ToolGateway to identify potential change regressions";
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
        ToolGateway toolGateway = context.getToolGateway();
        Incident incident = context.getIncident();
        InvestigationTask task = context.getTask();

        log.info("ChangeAnalysisAgent executing task: id={}, type='{}'",
                task != null ? task.getId() : "null", task != null ? task.getTaskType() : "null");

        if (toolGateway == null) {
            long durationMs = System.currentTimeMillis() - startTime;
            return AgentExecutionResult.failure(
                    AgentRunStatus.FAILED,
                    "TOOL_GATEWAY_UNAVAILABLE",
                    "ToolGateway is not available in agent execution context",
                    Map.of(),
                    durationMs
            );
        }

        // Determine owner and repository from task inputs or default
        String owner = "octocat";
        String repo = "Hello-World";

        if (context.getInputData() != null) {
            if (context.getInputData().containsKey("owner")) {
                owner = context.getInputData().get("owner").toString();
            }
            if (context.getInputData().containsKey("repository")) {
                repo = context.getInputData().get("repository").toString();
            }
        }

        // 1. Invoke GitHub commits tool through ToolGateway
        Map<String, Object> toolParams = new HashMap<>();
        toolParams.put("owner", owner);
        toolParams.put("repository", repo);
        toolParams.put("perPage", 5);

        ToolRequest toolRequest = ToolRequest.builder()
                .toolName("github.get_recent_commits")
                .parameters(toolParams)
                .investigationId(context.getInvestigation() != null ? context.getInvestigation().getId() : null)
                .taskId(task != null ? task.getId() : null)
                .build();

        ToolResult toolResult = toolGateway.invokeTool(toolRequest);

        // 2. If initial tool execution failed, evaluate and execute recovery
        if (!toolResult.isSuccess() && recoveryEngine != null) {
            int maxRetries = context.getInvestigation() != null && context.getInvestigation().getMaxRetriesPerTask() != null
                    ? context.getInvestigation().getMaxRetriesPerTask()
                    : 2;
            int maxAttempts = maxRetries + 1;
            int attempt = 1;

            while (!toolResult.isSuccess() && attempt <= maxAttempts) {
                RecoveryContext recoveryContext = RecoveryContext.forToolFailure(
                        toolResult,
                        toolRequest,
                        context,
                        attempt,
                        maxAttempts,
                        List.of()
                );
                recoveryContext.setFallbackToolName("github.get_pull_requests");

                RecoveryDecision decision = recoveryEngine.decide(recoveryContext);
                if (!decision.isShouldRecover()) {
                    log.info("Recovery engine decided not to recover (type={}): {}", decision.getRecoveryType(), decision.getReason());
                    break;
                }

                RecoveryResult recoveryResult = recoveryEngine.recover(recoveryContext, decision);
                if (recoveryResult.getRecoveredToolResult() != null) {
                    toolResult = recoveryResult.getRecoveredToolResult();
                }

                if (toolResult.isSuccess()) {
                    log.info("Tool invocation successfully recovered on attempt {}", attempt);
                    break;
                }

                attempt++;
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;

        if (!toolResult.isSuccess()) {
            log.warn("ChangeAnalysisAgent encountered tool failure: tool='{}', status='{}', error='{}'",
                    toolResult.getToolName(), toolResult.getStatus(), toolResult.getErrorMessage());

            Map<String, Object> failureOutput = new HashMap<>();
            failureOutput.put("toolName", toolResult.getToolName());
            failureOutput.put("toolStatus", toolResult.getStatus() != null ? toolResult.getStatus().name() : "FAILED");
            if (toolResult.getHttpStatus() != null) failureOutput.put("httpStatus", toolResult.getHttpStatus());
            if (toolResult.getErrorMessage() != null) failureOutput.put("errorMessage", toolResult.getErrorMessage());
            failureOutput.put("replanRecommended", true);

            return AgentExecutionResult.failure(
                    AgentRunStatus.FAILED,
                    toolResult.getErrorCode() != null ? toolResult.getErrorCode() : "TOOL_EXECUTION_FAILED",
                    "Tool invocation failed: " + toolResult.getErrorMessage(),
                    failureOutput,
                    durationMs
            );
        }

        // 2. Process real GitHub result into factual evidence
        List<EvidenceDraft> evidenceList = new ArrayList<>();
        Map<String, Object> responsePayload = toolResult.getResponsePayload();
        List<?> items = List.of();
        if (responsePayload != null) {
            if (responsePayload.get("items") instanceof List<?> list) {
                items = list;
            } else if (responsePayload.get("commits") instanceof List<?> list) {
                items = list;
            }
        }

        if (!items.isEmpty()) {
            Object firstCommitObj = items.get(0);
            String commitSha = "unknown";
            String commitMsg = "Recent repository commit";

            if (firstCommitObj instanceof Map<?, ?> commitMap) {
                if (commitMap.containsKey("sha")) {
                    commitSha = commitMap.get("sha").toString();
                }
                if (commitMap.get("commit") instanceof Map<?, ?> detailsMap && detailsMap.containsKey("message")) {
                    commitMsg = detailsMap.get("message").toString().trim();
                }
            }

            EvidenceDraft evidence = EvidenceDraft.builder()
                    .sourceType("GITHUB")
                    .sourceReference(String.format("repo:%s/%s#commit:%s", owner, repo, commitSha))
                    .claim(String.format("Repository '%s/%s' has recent commit %s with message: '%s'",
                            owner, repo, commitSha.substring(0, Math.min(commitSha.length(), 7)), commitMsg))
                    .rawData(Map.of(
                            "owner", owner,
                            "repository", repo,
                            "commitSha", commitSha,
                            "commitMessage", commitMsg,
                            "totalCommitsChecked", items.size()
                    ))
                    .confidence(new BigDecimal("0.9500"))
                    .toolCallId(toolResult.getToolCallId())
                    .build();

            evidenceList.add(evidence);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("owner", owner);
        output.put("repository", repo);
        output.put("commitsEvaluated", items.size());
        output.put("evidenceCount", evidenceList.size());
        output.put("summary", String.format("Analyzed %d recent commit(s) in repository %s/%s.", items.size(), owner, repo));

        List<String> hypotheses = List.of(
                "Code changes in recent commits may correlate with the reported error rate increase"
        );

        return AgentExecutionResult.success(output, evidenceList, hypotheses, List.of(), durationMs);
    }
}
