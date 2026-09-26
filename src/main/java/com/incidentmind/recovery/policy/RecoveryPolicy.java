package com.incidentmind.recovery.policy;

import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;

public interface RecoveryPolicy {

    RecoveryDecision evaluate(RecoveryContext context);
}
