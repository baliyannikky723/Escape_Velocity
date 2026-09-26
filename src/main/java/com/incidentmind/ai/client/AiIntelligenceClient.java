package com.incidentmind.ai.client;

import com.incidentmind.ai.dto.AiCriticRequest;
import com.incidentmind.ai.dto.AiCriticResponse;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeRequest;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeResponse;
import com.incidentmind.ai.dto.AiPlanRequest;
import com.incidentmind.ai.dto.AiPlanResponse;

public interface AiIntelligenceClient {

    AiPlanResponse plan(AiPlanRequest request);

    AiCriticResponse evaluate(AiCriticRequest request);

    AiEvidenceAnalyzeResponse analyzeEvidence(AiEvidenceAnalyzeRequest request);

    boolean isAvailable();
}
