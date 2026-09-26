package com.incidentmind.agent.core;

import com.incidentmind.agent.model.AgentExecutionContext;
import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.task.entity.InvestigationTask;

import java.util.Set;

public interface Agent {

    String getName();

    String getDescription();

    Set<String> getCapabilities();

    boolean canHandle(InvestigationTask task);

    AgentMetadata getMetadata();

    AgentExecutionResult execute(AgentExecutionContext context);
}
