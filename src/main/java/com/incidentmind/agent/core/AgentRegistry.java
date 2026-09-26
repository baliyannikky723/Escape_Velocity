package com.incidentmind.agent.core;

import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.task.entity.InvestigationTask;

import java.util.List;
import java.util.Optional;

public interface AgentRegistry {

    Optional<Agent> findAgentByName(String name);

    Agent getAgentByName(String name);

    Optional<Agent> findAgentForTask(InvestigationTask task);

    Agent getAgentForTask(InvestigationTask task);

    Optional<Agent> findAgentByCapability(String capability);

    List<AgentMetadata> getAvailableAgents();

    boolean containsAgent(String name);
}
