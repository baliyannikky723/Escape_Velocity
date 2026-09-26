package com.incidentmind.agent.core;

import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;

import java.util.UUID;

public interface AgentExecutionService {

    AgentExecutionResult executeTask(Incident incident,
                                     Investigation investigation,
                                     InvestigationTask task,
                                     UUID correlationId);
}
