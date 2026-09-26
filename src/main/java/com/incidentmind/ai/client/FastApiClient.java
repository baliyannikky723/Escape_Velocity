package com.incidentmind.ai.client;

import com.incidentmind.ai.config.AiServiceProperties;
import com.incidentmind.ai.dto.AiCriticRequest;
import com.incidentmind.ai.dto.AiCriticResponse;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeRequest;
import com.incidentmind.ai.dto.AiEvidenceAnalyzeResponse;
import com.incidentmind.ai.dto.AiPlanRequest;
import com.incidentmind.ai.dto.AiPlanResponse;
import com.incidentmind.common.filter.CorrelationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Component
public class FastApiClient implements AiIntelligenceClient {

    private final AiServiceProperties properties;
    private final RestClient restClient;

    public FastApiClient(AiServiceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiPlanResponse plan(AiPlanRequest request) {
        if (!properties.isEnabled()) {
            return AiPlanResponse.builder()
                    .investigationId(request.getInvestigationId())
                    .decision("CONTINUE")
                    .reason("AI service disabled via configuration; utilizing deterministic engine.")
                    .isFallback(true)
                    .build();
        }

        try {
            String correlationId = CorrelationContext.getCorrelationId();
            return restClient.post()
                    .uri("/ai/v1/plan")
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "none")
                    .body(request)
                    .retrieve()
                    .body(AiPlanResponse.class);
        } catch (Exception ex) {
            log.warn("FastAPI AI plan call failed ({}), falling back to deterministic planning: {}", ex.getClass().getSimpleName(), ex.getMessage());
            return AiPlanResponse.builder()
                    .investigationId(request.getInvestigationId())
                    .decision("CONTINUE")
                    .reason("FastAPI AI service call failed: " + ex.getMessage())
                    .isFallback(true)
                    .build();
        }
    }

    @Override
    public AiCriticResponse evaluate(AiCriticRequest request) {
        if (!properties.isEnabled()) {
            return AiCriticResponse.builder()
                    .investigationId(request.getInvestigationId())
                    .decision("ACCEPT")
                    .reason("AI service disabled; utilizing deterministic critic rules.")
                    .build();
        }

        try {
            String correlationId = CorrelationContext.getCorrelationId();
            return restClient.post()
                    .uri("/ai/v1/critic")
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "none")
                    .body(request)
                    .retrieve()
                    .body(AiCriticResponse.class);
        } catch (Exception ex) {
            log.warn("FastAPI AI critic call failed ({}), falling back to deterministic critic: {}", ex.getClass().getSimpleName(), ex.getMessage());
            return AiCriticResponse.builder()
                    .investigationId(request.getInvestigationId())
                    .decision("INCONCLUSIVE")
                    .reason("FastAPI AI service unavailable: " + ex.getMessage())
                    .build();
        }
    }

    @Override
    public AiEvidenceAnalyzeResponse analyzeEvidence(AiEvidenceAnalyzeRequest request) {
        if (!properties.isEnabled()) {
            return AiEvidenceAnalyzeResponse.builder().build();
        }

        try {
            String correlationId = CorrelationContext.getCorrelationId();
            if (request.getCorrelationId() == null) {
                request.setCorrelationId(correlationId);
            }
            return restClient.post()
                    .uri("/ai/v1/evidence/analyze")
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "none")
                    .body(request)
                    .retrieve()
                    .body(AiEvidenceAnalyzeResponse.class);
        } catch (Exception ex) {
            log.warn("FastAPI AI evidence analysis call failed: {}", ex.getMessage());
            return AiEvidenceAnalyzeResponse.builder()
                    .missingInformation(java.util.List.of("AI evidence analysis unavailable: " + ex.getMessage()))
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!properties.isEnabled()) return false;
        try {
            String resp = restClient.get()
                    .uri("/ai/v1/health")
                    .retrieve()
                    .body(String.class);
            return resp != null && resp.contains("UP");
        } catch (Exception ex) {
            return false;
        }
    }
}

