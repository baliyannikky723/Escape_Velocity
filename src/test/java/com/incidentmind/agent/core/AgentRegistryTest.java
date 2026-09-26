package com.incidentmind.agent.core;

import com.incidentmind.agent.model.AgentMetadata;
import com.incidentmind.agent.specialized.ChangeAnalysisAgent;
import com.incidentmind.agent.specialized.DependencyAnalysisAgent;
import com.incidentmind.agent.specialized.IncidentTriageAgent;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.tool.core.ToolGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRegistryTest {

    private DefaultAgentRegistry agentRegistry;
    private IncidentTriageAgent triageAgent;
    private ChangeAnalysisAgent changeAgent;
    private DependencyAnalysisAgent dependencyAgent;

    @BeforeEach
    void setUp() {
        triageAgent = new IncidentTriageAgent();
        changeAgent = new ChangeAnalysisAgent();
        dependencyAgent = new DependencyAnalysisAgent();

        agentRegistry = new DefaultAgentRegistry(List.of(triageAgent, changeAgent, dependencyAgent));
    }

    @Test
    @DisplayName("AgentRegistry: discovers and returns all registered specialized agents and metadata")
    void getAvailableAgents_ReturnsAllSpecialists() {
        List<AgentMetadata> metadataList = agentRegistry.getAvailableAgents();
        assertThat(metadataList).hasSize(3);

        List<String> names = metadataList.stream().map(AgentMetadata::getName).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "incident-triage-agent",
                "change-analysis-agent",
                "dependency-analysis-agent"
        );
    }

    @Test
    @DisplayName("AgentRegistry: resolves agent by task capability")
    void findAgentForTask_ByCapability() {
        InvestigationTask task1 = InvestigationTask.builder()
                .taskType("INCIDENT_TRIAGE")
                .build();
        Optional<Agent> agent1 = agentRegistry.findAgentForTask(task1);
        assertThat(agent1).isPresent();
        assertThat(agent1.get().getName()).isEqualTo("incident-triage-agent");

        InvestigationTask task2 = InvestigationTask.builder()
                .taskType("CHANGE_ANALYSIS")
                .build();
        Optional<Agent> agent2 = agentRegistry.findAgentForTask(task2);
        assertThat(agent2).isPresent();
        assertThat(agent2.get().getName()).isEqualTo("change-analysis-agent");

        InvestigationTask task3 = InvestigationTask.builder()
                .taskType("DEPENDENCY_ANALYSIS")
                .build();
        Optional<Agent> agent3 = agentRegistry.findAgentForTask(task3);
        assertThat(agent3).isPresent();
        assertThat(agent3.get().getName()).isEqualTo("dependency-analysis-agent");
    }

    @Test
    @DisplayName("AgentRegistry: resolves agent by assignedAgentType")
    void findAgentForTask_ByAssignedAgentType() {
        InvestigationTask task = InvestigationTask.builder()
                .taskType("CUSTOM_TASK_TYPE")
                .assignedAgentType("change-analysis-agent")
                .build();

        Optional<Agent> agent = agentRegistry.findAgentForTask(task);
        assertThat(agent).isPresent();
        assertThat(agent.get().getName()).isEqualTo("change-analysis-agent");
    }

    @Test
    @DisplayName("AgentRegistry: throws ResourceNotFoundException for unknown task capability")
    void getAgentForTask_UnknownTask_ThrowsException() {
        InvestigationTask unknownTask = InvestigationTask.builder()
                .taskType("UNKNOWN_CAPABILITY_XYZ")
                .build();

        assertThatThrownBy(() -> agentRegistry.getAgentForTask(unknownTask))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN_CAPABILITY_XYZ");
    }
}
