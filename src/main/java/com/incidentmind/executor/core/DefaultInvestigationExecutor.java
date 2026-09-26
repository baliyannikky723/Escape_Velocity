package com.incidentmind.executor.core;

import com.incidentmind.agent.core.AgentExecutionService;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.critic.core.InvestigationCritic;
import com.incidentmind.critic.model.CriticContext;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.executor.model.ExecutionContext;
import com.incidentmind.executor.model.ExecutionResult;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class DefaultInvestigationExecutor implements InvestigationExecutor {

    private final AgentExecutionService agentExecutionService;
    private final InvestigationCritic critic;
    private final AuditService auditService;
    private final InvestigationTaskRepository taskRepository;

    public DefaultInvestigationExecutor(AgentExecutionService agentExecutionService,
                                        InvestigationCritic critic,
                                        AuditService auditService,
                                        InvestigationTaskRepository taskRepository) {
        this.agentExecutionService = agentExecutionService;
        this.critic = critic;
        this.auditService = auditService;
        this.taskRepository = taskRepository;
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (context == null || context.getTask() == null) {
            return ExecutionResult.blocked("Null execution context or task");
        }

        InvestigationTask task = context.getTask();
        UUID investigationId = context.getInvestigation() != null ? context.getInvestigation().getId() : null;
        UUID correlationId = context.getCorrelationId();
        InvestigationBlackboard blackboard = context.getBlackboard();

        long startMs = System.currentTimeMillis();

        // 1. Validate dependencies via parentTaskId
        if (task.getParentTaskId() != null) {
            InvestigationTask parent = taskRepository.findById(task.getParentTaskId()).orElse(null);
            if (parent != null && parent.getStatus() != TaskStatus.COMPLETED && parent.getStatus() != TaskStatus.FAILED) {
                log.info("Task '{}' blocked because parent task '{}' has status '{}'",
                        task.getId(), parent.getId(), parent.getStatus());
                task.setStatus(TaskStatus.BLOCKED);
                taskRepository.save(task);
                return ExecutionResult.blocked("Parent task " + parent.getId() + " is not yet finished");
            }
        }

        // 2. Emit EXECUTOR_STARTED audit event
        Map<String, Object> execStartData = new HashMap<>();
        execStartData.put("taskId", task.getId().toString());
        execStartData.put("taskType", task.getTaskType());
        if (task.getTitle() != null) execStartData.put("title", task.getTitle());

        auditService.recordEvent(
                investigationId,
                task.getId(),
                null,
                AuditEventType.EXECUTOR_STARTED,
                ActorType.ORCHESTRATOR,
                "investigation-executor",
                execStartData,
                correlationId
        );

        // 3. Execute task via AgentExecutionService
        AgentExecutionResult agentResult = agentExecutionService.executeTask(
                context.getIncident(),
                context.getInvestigation(),
                task,
                correlationId
        );

        List<Evidence> priorEvidence = blackboard != null ? blackboard.getEvidence() : List.of();

        // 4. Build Critic Context and invoke Critic validation
        CriticContext criticContext = CriticContext.builder()
                .incident(context.getIncident())
                .investigation(context.getInvestigation())
                .task(task)
                .agentResult(agentResult)
                .priorEvidence(priorEvidence)
                .newlyCreatedDrafts(agentResult.getEvidenceDrafts())
                .correlationId(correlationId)
                .build();

        CriticResult criticResult = critic.evaluate(criticContext);
        if (blackboard != null) {
            blackboard.recordCriticResult(criticResult);
        }

        long totalDurationMs = System.currentTimeMillis() - startMs;

        // 5. Handle Critic decisions
        if (criticResult.isHumanApprovalRequired()) {
            log.warn("Critic required human approval for task '{}'", task.getId());
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);
            return ExecutionResult.rejected(TaskStatus.FAILED, agentResult, criticResult, totalDurationMs);
        }

        if (criticResult.isRejected()) {
            log.warn("Critic REJECTED agent findings for task '{}': {}", task.getId(), criticResult.getReasons());
            task.setStatus(TaskStatus.FAILED);
            taskRepository.save(task);
            return ExecutionResult.rejected(TaskStatus.FAILED, agentResult, criticResult, totalDurationMs);
        }

        // Output accepted by Critic
        log.info("Critic ACCEPTED findings for task '{}'", task.getId());
        return ExecutionResult.accepted(task.getStatus(), agentResult, criticResult, totalDurationMs);
    }
}
