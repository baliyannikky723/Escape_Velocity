package com.incidentmind.stopping.core;

import com.incidentmind.stopping.model.StoppingContext;
import com.incidentmind.stopping.model.StoppingResult;

public interface InvestigationStoppingPolicy {

    StoppingResult evaluate(StoppingContext context);
}
