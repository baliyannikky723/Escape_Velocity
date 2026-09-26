package com.incidentmind.planner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationPlan {

    private String reasoningSummary;

    @Builder.Default
    private List<PlannedTask> tasks = new ArrayList<>();

    @Builder.Default
    private PlanAction nextAction = PlanAction.EXECUTE_TASKS;

    private boolean finalPlan;

    public static InvestigationPlan empty(String reasoning) {
        return InvestigationPlan.builder()
                .reasoningSummary(reasoning)
                .tasks(List.of())
                .nextAction(PlanAction.NO_FURTHER_TASKS)
                .finalPlan(true)
                .build();
    }
}
