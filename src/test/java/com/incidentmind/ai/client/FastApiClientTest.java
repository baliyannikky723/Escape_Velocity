package com.incidentmind.ai.client;

import com.incidentmind.ai.config.AiServiceProperties;
import com.incidentmind.ai.dto.AiCriticRequest;
import com.incidentmind.ai.dto.AiCriticResponse;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeRequest;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeResponse;
import com.incidentmind.ai.dto.AiPlanRequest;
import com.incidentmind.ai.dto.AiPlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FastApiClientTest {

    @Test
    @DisplayName("FastApiClient: when AI service disabled, plan returns deterministic fallback response")
    void plan_ServiceDisabled_ReturnsFallback() {
        AiServiceProperties props = new AiServiceProperties();
        props.setEnabled(false);
        props.setBaseUrl("http://localhost:8000");

        FastApiClient client = new FastApiClient(props);

        AiPlanRequest request = AiPlanRequest.builder()
                .investigationId(UUID.randomUUID())
                .incident(Map.of("key", "INC-123"))
                .build();

        AiPlanResponse response = client.plan(request);

        assertThat(response).isNotNull();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getReason()).contains("disabled via configuration");
    }

    @Test
    @DisplayName("FastApiClient: when AI service disabled, critic returns deterministic ACCEPT")
    void critic_ServiceDisabled_ReturnsAccept() {
        AiServiceProperties props = new AiServiceProperties();
        props.setEnabled(false);
        props.setBaseUrl("http://localhost:8000");

        FastApiClient client = new FastApiClient(props);

        AiCriticRequest request = AiCriticRequest.builder()
                .investigationId(UUID.randomUUID())
                .task(Map.of("taskType", "INCIDENT_TRIAGE"))
                .build();

        AiCriticResponse response = client.evaluate(request);

        assertThat(response).isNotNull();
        assertThat(response.getDecision()).isEqualTo("ACCEPT");
    }

    @Test
    @DisplayName("FastApiClient: when AI service connection fails, gracefully falls back")
    void plan_ConnectionFailure_GracefulFallback() {
        AiServiceProperties props = new AiServiceProperties();
        props.setEnabled(true);
        props.setBaseUrl("http://127.0.0.1:59999"); // unreachable port
        props.setTimeoutSeconds(1);

        FastApiClient client = new FastApiClient(props);

        AiPlanRequest request = AiPlanRequest.builder()
                .investigationId(UUID.randomUUID())
                .incident(Map.of("key", "INC-123"))
                .build();

        AiPlanResponse response = client.plan(request);

        assertThat(response).isNotNull();
        assertThat(response.isFallback()).isTrue();
        assertThat(response.getReason()).contains("failed");
    }
}
