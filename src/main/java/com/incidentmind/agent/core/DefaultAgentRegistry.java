package com.incidentmind.agent.core;

import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.task.entity.InvestigationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DefaultAgentRegistry implements AgentRegistry {

    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();

    public DefaultAgentRegistry(List<Agent> agents) {
        if (agents != null) {
            for (Agent agent : agents) {
                registerAgent(agent);
            }
        }
    }

    public void registerAgent(Agent agent) {
        if (agent == null || agent.getName() == null) {
            throw new IllegalArgumentException("Agent and agent name must not be null");
        }
        agentMap.put(agent.getName(), agent);
        log.info("Registered agent: name='{}', capabilities={}", agent.getName(), agent.getCapabilities());
    }

    @Override
    public Optional<Agent> findAgentByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agentMap.get(name));
    }

    @Override
    public Agent getAgentByName(String name) {
        return findAgentByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", name));
    }

    @Override
    public Optional<Agent> findAgentForTask(InvestigationTask task) {
        if (task == null) {
            return Optional.empty();
        }

        // 1. If assignedAgentType is explicitly set, check by name
        if (task.getAssignedAgentType() != null && !task.getAssignedAgentType().isBlank()) {
            Agent agent = agentMap.get(task.getAssignedAgentType());
            if (agent != null) {
                return Optional.of(agent);
            }
        }

        // 2. Otherwise search across agents via canHandle(task)
        for (Agent agent : agentMap.values()) {
            if (agent.canHandle(task)) {
                return Optional.of(agent);
            }
        }

        // 3. Check by matching taskType with agent capability
        return findAgentByCapability(task.getTaskType());
    }

    @Override
    public Agent getAgentForTask(InvestigationTask task) {
        return findAgentForTask(task)
                .orElseThrow(() -> new ResourceNotFoundException("Agent for task " + (task != null ? task.getTaskType() : "null")));
    }

    @Override
    public Optional<Agent> findAgentByCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return Optional.empty();
        }
        String targetCap = capability.trim().toUpperCase();
        for (Agent agent : agentMap.values()) {
            if (agent.getCapabilities() != null) {
                for (String cap : agent.getCapabilities()) {
                    if (cap.equalsIgnoreCase(targetCap)) {
                        return Optional.of(agent);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<AgentMetadata> getAvailableAgents() {
        return agentMap.values().stream()
                .map(Agent::getMetadata)
                .toList();
    }

    @Override
    public boolean containsAgent(String name) {
        return name != null && agentMap.containsKey(name);
    }
}
