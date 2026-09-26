package com.incidentmind.planner.core;

import com.incidentmind.planner.model.InvestigationPlan;

public interface InvestigationPlanner {

    InvestigationPlan plan(PlannerContext context);
}
