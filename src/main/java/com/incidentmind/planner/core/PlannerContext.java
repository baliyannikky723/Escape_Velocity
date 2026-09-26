package com.incidentmind.planner.core;

import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerContext {

    private Incident incident;
    private Investigation investigation;

    @Builder.Default
    private List<InvestigationTask> existingTasks = new ArrayList<>();

    @Builder.Default
    private List<Evidence> evidence = new ArrayList<>();

    private UUID correlationId;
}
