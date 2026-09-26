package com.incidentmind.stopping.core;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.stopping.model.StoppingContext;
import com.incidentmind.stopping.model.StoppingResult;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class DefaultInvestigationStoppingPolicy implements InvestigationStoppingPolicy {

    private final AuditService auditService;

    public DefaultInvestigationStoppingPolicy(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public StoppingResult evaluate(StoppingContext context) {
        if (context == null || context.getInvestigation() == null) {
            return StoppingResult.stopBlocked("Stopping context or investigation is null");
        }

        Investigation investigation = context.getInvestigation();
        UUID investigationId = investigation.getId();
        UUID correlationId = context.getCorrelationId();

        int maxTasks = investigation.getMaxTasks() != null ? investigation.getMaxTasks() : 15;
        int maxRuntimeSeconds = investigation.getMaxRuntimeSeconds() != null ? investigation.getMaxRuntimeSeconds() : 300;

        List<InvestigationTask> tasks = context.getTasks() != null ? context.getTasks() : List.of();
        long runtimeSeconds = context.getRuntimeSeconds();

        Map<String, Object> checkData = new HashMap<>();
        checkData.put("taskCount", tasks.size());
        checkData.put("maxTasks", maxTasks);
        checkData.put("runtimeSeconds", runtimeSeconds);
        checkData.put("maxRuntimeSeconds", maxRuntimeSeconds);
        checkData.put("planHasNoFurtherTasks", context.isPlanHasNoFurtherTasks());

        StoppingResult result;

        // 1. Check: Human approval pending
        boolean humanApprovalNeeded = context.isHumanApprovalPending()
                || (context.getBlackboard() != null && context.getBlackboard().getCriticResults() != null
                && context.getBlackboard().getCriticResults().stream().anyMatch(com.incidentmind.critic.model.CriticResult::isHumanApprovalRequired));

        if (humanApprovalNeeded) {
            log.info("Stopping check: Human approval is pending; stopping automated execution.");
            result = StoppingResult.humanApprovalRequired("Human engineer approval required for proposed sensitive production action.");
        }
        // 2. Check: Runtime exceeded
        else if (runtimeSeconds >= maxRuntimeSeconds) {
            log.warn("Stopping check: Runtime limit exceeded ({}s >= {}s)", runtimeSeconds, maxRuntimeSeconds);
            result = StoppingResult.stopTimeout("Investigation exceeded configured maximum runtime limit", checkData);
        }
        // 3. Check: Maximum task budget reached
        else if (tasks.size() >= maxTasks) {
            log.warn("Stopping check: Task limit reached ({} >= {})", tasks.size(), maxTasks);
            result = StoppingResult.stopLimitReached("Maximum allowed investigation task budget reached", checkData);
        }
        // 4. Check: All remaining tasks are blocked
        else if (!tasks.isEmpty() && tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.BLOCKED)) {
            log.warn("Stopping check: All remaining tasks are blocked");
            result = StoppingResult.stopBlocked("All remaining tasks in dependency graph are blocked");
        }
        // 5. Check: Successful completion (No further tasks planned and all existing tasks finished)
        else if (context.isPlanHasNoFurtherTasks() && !tasks.isEmpty() && tasks.stream().allMatch(
                t -> t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.FAILED || t.getStatus() == TaskStatus.SKIPPED)) {
            log.info("Stopping check: Investigation successfully completed with verified evidence");
            result = StoppingResult.stopSuccess("Investigation objective satisfied; all planned tasks finished with verified evidence", checkData);
        }
        // 6. Otherwise: Continue
        else {
            result = StoppingResult.continueInvestigation("Investigation conditions allow continued task execution");
        }

        checkData.put("decision", result.getDecision().name());
        checkData.put("shouldStop", result.isShouldStop());
        checkData.put("reason", result.getReason());

        auditService.recordEvent(
                investigationId,
                null,
                null,
                AuditEventType.STOPPING_CHECK,
                ActorType.ORCHESTRATOR,
                "stopping-policy",
                checkData,
                correlationId
        );

        return result;
    }
}
