package com.incidentmind.critic.core;

import com.incidentmind.critic.model.CriticContext;
import com.incidentmind.critic.model.CriticResult;

public interface InvestigationCritic {

    CriticResult evaluate(CriticContext context);
}
