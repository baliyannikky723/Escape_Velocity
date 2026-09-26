package com.incidentmind.agent.core;

import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.entity.AgentRunStatus;
import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import com.incidentmind.tool.core.PayloadSanitizer;
import com.incidentmind.tool.core.ToolGateway;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DefaultAgentExecutionService implements AgentExecutionService {

    private final AgentRegistry agentRegistry;
    private final AgentRunRepository agentRunRepository;
    private final InvestigationTaskRepository taskRepository;
    private final EvidenceRepository evidenceRepository;
    private final AuditService auditService;
    private final ToolGateway toolGateway;
    private final com.incidentmind.recovery.core.RecoveryEngine recoveryEngine;

    public DefaultAgentExecutionService(AgentRegistry agentRegistry,
                                        AgentRunRepository agentRunRepository,
                                        InvestigationTaskRepository taskRepository,
                                        EvidenceRepository evidenceRepository,
                                        AuditService auditService,
                                        ToolGateway toolGateway) {
        this(agentRegistry, agentRunRepository, taskRepository, evidenceRepository, auditService, toolGateway, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultAgentExecutionService(AgentRegistry agentRegistry,
                                        AgentRunRepository agentRunRepository,
                                        InvestigationTaskRepository taskRepository,
                                        EvidenceRepository evidenceRepository,
                                        AuditService auditService,
                                        ToolGateway toolGateway,
                                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                                        com.incidentmind.recovery.core.RecoveryEngine recoveryEngine) {
        this.agentRegistry = agentRegistry;
        this.agentRunRepository = agentRunRepository;
        this.taskRepository = taskRepository;
        this.evidenceRepository = evidenceRepository;
        this.auditService = auditService;
        this.toolGateway = toolGateway;
        this.recoveryEngine = recoveryEngine;
    }

    @Override
    @Transactional
    public AgentExecutionResult executeTask(Incident incident,
                                            Investigation investigation,
                                            InvestigationTask task,
                                            UUID correlationId) {
        if (task == null) {
            throw new IllegalArgumentException("Task must not be null");
        }

        Agent agent = agentRegistry.getAgentForTask(task);
        Instant now = Instant.now();
        long startTime = System.currentTimeMillis();

        // 1. Create and persist AgentRun in STARTED status
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("taskId", task.getId().toString());
        inputPayload.put("taskType", task.getTaskType());
        inputPayload.put("title", task.getTitle());
        if (incident != null) {
            inputPayload.put("incidentId", incident.getId().toString());
            inputPayload.put("incidentKey", incident.getIncidentKey());
        }

        AgentRun agentRun = AgentRun.builder()
                .id(UUID.randomUUID())
                .taskId(task.getId())
                .agentType(agent.getName())
                .status(AgentRunStatus.STARTED)
                .inputPayload(PayloadSanitizer.sanitize(inputPayload))
                .startedAt(now)
                .createdAt(now)
                .build();

        agentRun = agentRunRepository.save(agentRun);

        // 2. Update Task to RUNNING
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(now);
        task.setAttemptCount(task.getAttemptCount() != null ? task.getAttemptCount() + 1 : 1);
        task.setAssignedAgentType(agent.getName());
        taskRepository.save(task);

        // 3. Emit AGENT_STARTED and TASK_STARTED audit events
        UUID investigationId = investigation != null ? investigation.getId() : null;

        Map<String, Object> taskStartedData = new HashMap<>();
        taskStartedData.put("taskType", task.getTaskType());
        if (task.getTitle() != null) taskStartedData.put("title", task.getTitle());

        auditService.recordEvent(
                investigationId,
                task.getId(),
                agentRun.getId(),
                AuditEventType.TASK_STARTED,
                ActorType.AGENT,
                agent.getName(),
                taskStartedData,
                correlationId
        );

        Map<String, Object> agentStartedData = new HashMap<>();
        agentStartedData.put("agentType", agent.getName());
        if (task.getId() != null) agentStartedData.put("taskId", task.getId().toString());

        auditService.recordEvent(
                investigationId,
                task.getId(),
                agentRun.getId(),
                AuditEventType.AGENT_STARTED,
                ActorType.AGENT,
                agent.getName(),
                agentStartedData,
                correlationId
        );

        // 4. Execute the Agent
        List<Evidence> priorEvidence = List.of();
        if (investigationId != null && evidenceRepository != null) {
            List<Evidence> found = evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigationId);
            if (found != null) {
                priorEvidence = found;
            }
        }

        AgentExecutionContext executionContext = AgentExecutionContext.builder()
                .incident(incident)
                .investigation(investigation)
                .task(task)
                .priorEvidence(priorEvidence)
                .correlationId(correlationId)
                .toolGateway(toolGateway)
                .inputData(inputPayload)
                .build();

        AgentExecutionResult result;
        try {
            result = agent.execute(executionContext);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Unhandled exception executing agent '{}' on task '{}'", agent.getName(), task.getId(), ex);
            Map<String, Object> errData = new HashMap<>();
            errData.put("error", ex.getClass().getSimpleName());
            if (ex.getMessage() != null) errData.put("message", ex.getMessage());
            result = AgentExecutionResult.failure(
                    AgentRunStatus.FAILED,
                    "AGENT_EXECUTION_EXCEPTION",
                    ex.getMessage() != null ? ex.getMessage() : "Unknown exception",
                    errData,
                    durationMs
            );
        }

        // Evaluate agent-level recovery if initial execution failed
        if (!result.isSuccess() && recoveryEngine != null) {
            int maxRetries = investigation != null && investigation.getMaxRetriesPerTask() != null
                    ? investigation.getMaxRetriesPerTask()
                    : 2;
            int maxAttempts = maxRetries + 1;
            int attempt = 1;

            while (!result.isSuccess() && attempt <= maxAttempts) {
                RecoveryContext recoveryContext = RecoveryContext.forAgentFailure(
                        result,
                        executionContext,
                        attempt,
                        maxAttempts,
                        List.of()
                );

                RecoveryDecision decision = recoveryEngine.decide(recoveryContext);
                if (!decision.isShouldRecover()) {
                    break;
                }

                try {
                    result = agent.execute(executionContext);
                    if (result.isSuccess()) {
                        log.info("Agent '{}' successfully recovered on attempt {}", agent.getName(), attempt);
                        break;
                    }
                } catch (Exception ex) {
                    log.warn("Agent retry attempt {} encountered exception: {}", attempt, ex.getMessage());
                }

                attempt++;
            }
        }

        long totalDurationMs = System.currentTimeMillis() - startTime;
        Instant completedTime = Instant.now();

        // 5. Persist Evidence items produced by the agent
        if (result.isSuccess() && result.getEvidenceDrafts() != null) {
            for (EvidenceDraft draft : result.getEvidenceDrafts()) {
                Evidence evidence = Evidence.builder()
                        .id(UUID.randomUUID())
                        .investigationId(investigationId)
                        .taskId(task.getId())
                        .agentRunId(agentRun.getId())
                        .toolCallId(draft.getToolCallId())
                        .sourceType(draft.getSourceType())
                        .sourceReference(draft.getSourceReference())
                        .claim(draft.getClaim())
                        .rawData(PayloadSanitizer.sanitize(draft.getRawData()))
                        .confidence(draft.getConfidence())
                        .collectedAt(completedTime)
                        .createdAt(completedTime)
                        .build();

                evidence = evidenceRepository.save(evidence);

                Map<String, Object> evidenceAuditData = new HashMap<>();
                evidenceAuditData.put("evidenceId", evidence.getId().toString());
                evidenceAuditData.put("sourceType", evidence.getSourceType());
                evidenceAuditData.put("claim", evidence.getClaim());
                evidenceAuditData.put("confidence", evidence.getConfidence());

                auditService.recordEvent(
                        investigationId,
                        task.getId(),
                        agentRun.getId(),
                        AuditEventType.EVIDENCE_CREATED,
                        ActorType.AGENT,
                        agent.getName(),
                        evidenceAuditData,
                        correlationId
                );
            }
        }

        // 6. Update AgentRun record
        agentRun.setStatus(result.getStatus());
        agentRun.setOutputPayload(PayloadSanitizer.sanitize(result.getOutputPayload()));
        agentRun.setErrorCode(result.getErrorCode());
        agentRun.setErrorMessage(result.getErrorMessage());
        agentRun.setCompletedAt(completedTime);
        agentRun.setDurationMs(totalDurationMs);
        agentRunRepository.save(agentRun);

        // 7. Update Task status
        if (result.isSuccess()) {
            task.setStatus(TaskStatus.COMPLETED);
        } else {
            task.setStatus(TaskStatus.FAILED);
        }
        task.setCompletedAt(completedTime);
        taskRepository.save(task);

        // 8. Emit completion / failure audit events
        AuditEventType agentEventType = result.isSuccess() ? AuditEventType.AGENT_COMPLETED : AuditEventType.AGENT_FAILED;
        AuditEventType taskEventType = result.isSuccess() ? AuditEventType.TASK_COMPLETED : AuditEventType.TASK_FAILED;

        auditService.recordEvent(
                investigationId,
                task.getId(),
                agentRun.getId(),
                agentEventType,
                ActorType.AGENT,
                agent.getName(),
                Map.of("status", result.getStatus().name(), "durationMs", totalDurationMs),
                correlationId
        );

        auditService.recordEvent(
                investigationId,
                task.getId(),
                agentRun.getId(),
                taskEventType,
                ActorType.AGENT,
                agent.getName(),
                Map.of("status", task.getStatus().name(), "durationMs", totalDurationMs),
                correlationId
        );

        log.info("Agent '{}' finished task '{}' with status '{}' in {}ms",
                agent.getName(), task.getId(), result.getStatus(), totalDurationMs);

        return result;
    }
}
