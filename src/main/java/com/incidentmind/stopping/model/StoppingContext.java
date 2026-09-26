package com.incidentmind.stopping.model;

import com.incidentmind.blackboard.core.InvestigationBlackboard;
import com.incidentmind.evidence.entity.Evidence;
import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.task.entity.InvestigationTask;
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
public class StoppingContext {

    private Investigation investigation;
    private InvestigationBlackboard blackboard;
    @Builder.Default
    private List<InvestigationTask> tasks = List.of();
    @Builder.Default
    private List<Evidence> evidence = List.of();
    private long runtimeSeconds;
    private boolean planHasNoFurtherTasks;
    private boolean humanApprovalPending;
    private UUID correlationId;
}
