package com.incidentmind.recovery.core;

import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.recovery.model.RecoveryResult;

public interface RecoveryEngine {

    RecoveryDecision decide(RecoveryContext context);

    RecoveryResult recover(RecoveryContext context, RecoveryDecision decision);
}
