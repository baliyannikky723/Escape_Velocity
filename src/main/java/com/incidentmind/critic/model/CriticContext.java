package com.incidentmind.critic.model;

import com.incidentmind.agent.model.AgentExecutionResult;
import com.incidentmind.agent.model.EvidenceDraft;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.tool.entity.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticContext {

    private Incident incident;
    private Investigation investigation;
    private InvestigationTask task;
    private AgentExecutionResult agentResult;
    @Builder.Default
    private List<Evidence> priorEvidence = List.of();
    @Builder.Default
    private List<EvidenceDraft> newlyCreatedDrafts = List.of();
    @Builder.Default
    private List<ToolCall> taskToolCalls = List.of();
    private UUID correlationId;
}
