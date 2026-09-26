package com.incidentmind.report.core;

import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.critic.dto.CriticEvaluationResponse;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.recovery.dto.RecoveryAttemptResponse;
import com.incidentmind.recovery.entity.RecoveryAttempt;
import com.incidentmind.report.dto.InvestigationReportResponse;
import com.incidentmind.task.entity.InvestigationTask;

import java.util.List;

public interface InvestigationSynthesizer {

    InvestigationReportResponse synthesizeReport(
            Incident incident,
            Investigation investigation,
            List<InvestigationTask> tasks,
            List<Evidence> evidenceList,
            List<RecoveryAttempt> recoveryAttempts,
            List<AuditEvent> auditEvents,
            InvestigationBlackboard blackboard
    );
}
