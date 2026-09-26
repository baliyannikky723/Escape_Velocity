package com.incidentmind.executor.model;

import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {

    private Incident incident;
    private Investigation investigation;
    private InvestigationTask task;
    private InvestigationBlackboard blackboard;
    private UUID correlationId;
}
