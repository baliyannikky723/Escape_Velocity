package com.incidentmind.blackboard.core;

import com.incidentmind.agent.entity.AgentRun;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import com.incidentmind.critic.model.CriticResult;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.task.entity.InvestigationTask;

import java.util.List;
import java.util.UUID;

public interface InvestigationBlackboard {

    UUID getInvestigationId();

    Incident getIncident();

    Investigation getInvestigation();

    List<InvestigationTask> getTasks();

    List<Evidence> getEvidence();

    List<AgentRun> getAgentRuns();

    List<RecoveryAttempt> getRecoveryAttempts();

    List<CriticResult> getCriticResults();

    InvestigationMetrics getMetrics();

    void recordTask(InvestigationTask task);

    void recordEvidence(Evidence evidence);

    void recordAgentRun(AgentRun agentRun);

    void recordCriticResult(CriticResult criticResult);

    void recordRecoveryAttempt(RecoveryAttempt attempt);

    void refresh();
}
