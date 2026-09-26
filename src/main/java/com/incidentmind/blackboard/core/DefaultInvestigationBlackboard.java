package com.incidentmind.blackboard.core;

import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.agent.repository.AgentRunRepository;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.evidence.repository.EvidenceRepository;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.recovery.repository.RecoveryAttemptRepository;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskStatus;
import com.incidentmind.task.repository.InvestigationTaskRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class DefaultInvestigationBlackboard implements InvestigationBlackboard {

    private final Incident incident;
    private final Investigation investigation;
    private final InvestigationTaskRepository taskRepository;
    private final EvidenceRepository evidenceRepository;
    private final AgentRunRepository agentRunRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;

    private final List<CriticResult> criticResults = new CopyOnWriteArrayList<>();

    public DefaultInvestigationBlackboard(Incident incident,
                                          Investigation investigation,
                                          InvestigationTaskRepository taskRepository,
                                          EvidenceRepository evidenceRepository,
                                          AgentRunRepository agentRunRepository,
                                          RecoveryAttemptRepository recoveryAttemptRepository) {
        this.incident = incident;
        this.investigation = investigation;
        this.taskRepository = taskRepository;
        this.evidenceRepository = evidenceRepository;
        this.agentRunRepository = agentRunRepository;
        this.recoveryAttemptRepository = recoveryAttemptRepository;
    }

    @Override
    public UUID getInvestigationId() {
        return investigation != null ? investigation.getId() : null;
    }

    @Override
    public Incident getIncident() {
        return incident;
    }

    @Override
    public Investigation getInvestigation() {
        return investigation;
    }

    @Override
    public List<InvestigationTask> getTasks() {
        if (taskRepository == null || investigation == null) return List.of();
        List<InvestigationTask> tasks = taskRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
        return tasks != null ? tasks : List.of();
    }

    @Override
    public List<Evidence> getEvidence() {
        if (evidenceRepository == null || investigation == null) return List.of();
        List<Evidence> evidence = evidenceRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
        return evidence != null ? evidence : List.of();
    }

    @Override
    public List<AgentRun> getAgentRuns() {
        if (agentRunRepository == null || investigation == null) return List.of();
        List<InvestigationTask> tasks = getTasks();
        List<AgentRun> runs = new ArrayList<>();
        for (InvestigationTask t : tasks) {
            List<AgentRun> taskRuns = agentRunRepository.findByTaskIdOrderByCreatedAtAsc(t.getId());
            if (taskRuns != null) runs.addAll(taskRuns);
        }
        return runs;
    }

    @Override
    public List<RecoveryAttempt> getRecoveryAttempts() {
        if (recoveryAttemptRepository == null || investigation == null) return List.of();
        List<RecoveryAttempt> attempts = recoveryAttemptRepository.findByInvestigationIdOrderByCreatedAtAsc(investigation.getId());
        return attempts != null ? attempts : List.of();
    }

    @Override
    public List<CriticResult> getCriticResults() {
        return new ArrayList<>(criticResults);
    }

    @Override
    public InvestigationMetrics getMetrics() {
        List<InvestigationTask> tasks = getTasks();
        List<AgentRun> runs = getAgentRuns();
        List<RecoveryAttempt> recoveries = getRecoveryAttempts();

        int completed = 0;
        int failed = 0;
        int rejected = 0;
        int skipped = 0;
        int blocked = 0;

        for (InvestigationTask t : tasks) {
            if (t.getStatus() == TaskStatus.COMPLETED) completed++;
            else if (t.getStatus() == TaskStatus.FAILED) failed++;
            else if (t.getStatus() == TaskStatus.SKIPPED) skipped++;
            else if (t.getStatus() == TaskStatus.BLOCKED) blocked++;
        }

        int criticRejections = 0;
        int criticAcceptances = 0;
        for (CriticResult cr : criticResults) {
            if (cr.isAccepted()) criticAcceptances++;
            else if (cr.isRejected()) criticRejections++;
        }

        long durationMs = 0;
        if (investigation != null && investigation.getStartedAt() != null) {
            durationMs = System.currentTimeMillis() - investigation.getStartedAt().toEpochMilli();
        }

        return InvestigationMetrics.builder()
                .totalDurationMs(durationMs)
                .agentRunsCount(runs.size())
                .toolCallsCount(runs.size()) // 1-to-1 or bounded by runs
                .recoveryAttemptsCount(recoveries.size())
                .criticEvaluationsCount(criticResults.size())
                .criticAcceptancesCount(criticAcceptances)
                .criticRejectionsCount(criticRejections)
                .tasksCreatedCount(tasks.size())
                .tasksCompletedCount(completed)
                .tasksFailedCount(failed)
                .tasksRejectedCount(criticRejections)
                .tasksSkippedCount(skipped)
                .tasksBlockedCount(blocked)
                .build();
    }

    @Override
    public void recordTask(InvestigationTask task) {
        if (taskRepository != null && task != null) {
            taskRepository.save(task);
        }
    }

    @Override
    public void recordEvidence(Evidence evidence) {
        if (evidenceRepository != null && evidence != null) {
            evidenceRepository.save(evidence);
        }
    }

    @Override
    public void recordAgentRun(AgentRun agentRun) {
        if (agentRunRepository != null && agentRun != null) {
            agentRunRepository.save(agentRun);
        }
    }

    @Override
    public void recordCriticResult(CriticResult criticResult) {
        if (criticResult != null) {
            criticResults.add(criticResult);
        }
    }

    @Override
    public void recordRecoveryAttempt(RecoveryAttempt attempt) {
        if (recoveryAttemptRepository != null && attempt != null) {
            recoveryAttemptRepository.save(attempt);
        }
    }

    @Override
    public void refresh() {
        // Relational reads are queried fresh on getTasks(), getEvidence(), etc.
    }
}
