package com.incidentmind.agent.model;

import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.tool.core.ToolGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionContext {

    private Incident incident;
    private Investigation investigation;
    private InvestigationTask task;
    private List<Evidence> priorEvidence;
    private UUID correlationId;
    private ToolGateway toolGateway;

    @Builder.Default
    private Map<String, Object> inputData = new HashMap<>();
}
