package com.incidentmind.executor.core;

import com.incidentmind.executor.model.ExecutionContext;
import com.incidentmind.executor.model.ExecutionResult;

public interface InvestigationExecutor {

    ExecutionResult execute(ExecutionContext context);
}
