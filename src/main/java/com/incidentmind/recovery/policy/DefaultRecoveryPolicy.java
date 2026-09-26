package com.incidentmind.recovery.policy;

import com.incidentmind.recovery.config.RecoveryProperties;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.tool.model.ErrorClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DefaultRecoveryPolicy implements RecoveryPolicy {

    private final RecoveryProperties properties;
    private final BackoffStrategy backoffStrategy;

    public DefaultRecoveryPolicy(RecoveryProperties properties, BackoffStrategy backoffStrategy) {
        this.properties = properties;
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public RecoveryDecision evaluate(RecoveryContext context) {
        if (context == null) {
            return RecoveryDecision.abort("Empty recovery context", Map.of());
        }

        if (!properties.isEnabled()) {
            return RecoveryDecision.replan("Recovery engine is disabled", Map.of("enabled", false));
        }

        int currentAttempt = context.getAttemptNumber();
        int maxAttempts = context.getMaxAttempts() > 0 ? context.getMaxAttempts() : properties.getMaxAttempts();

        Integer httpStatus = context.getHttpStatus();
        ErrorClassification classification = context.getErrorClassification() != null
                ? context.getErrorClassification()
                : ErrorClassification.UNKNOWN;

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("attemptNumber", currentAttempt);
        auditData.put("maxAttempts", maxAttempts);
        if (httpStatus != null) auditData.put("httpStatus", httpStatus);
        auditData.put("errorClassification", classification.name());
        if (context.getErrorCode() != null) auditData.put("errorCode", context.getErrorCode());

        // 1. Permanent non-recoverable errors: HTTP 401 / 403 (Authorization) & VALIDATION_ERROR
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
            log.warn("Permanent authorization error ({}); aborting recovery", httpStatus);
            return RecoveryDecision.abort(
                    "Permanent authentication or authorization error (HTTP " + httpStatus + ")",
                    auditData
            );
        }

        if (classification == ErrorClassification.VALIDATION_ERROR) {
            log.warn("Permanent validation error; aborting recovery");
            return RecoveryDecision.abort("Invalid request payload or schema validation error", auditData);
        }

        // 2. Resource not found: HTTP 404
        if (httpStatus != null && httpStatus == 404) {
            log.info("Target resource not found (HTTP 404); recommending replan");
            return RecoveryDecision.replan(
                    "Target resource not found (HTTP 404); recommending alternative investigation path",
                    auditData
            );
        }

        // 3. Rate limiting: HTTP 429
        if (httpStatus != null && httpStatus == 429) {
            if (currentAttempt < maxAttempts) {
                long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
                auditData.put("backoffMs", backoffMs);
                log.info("Rate limit encountered (HTTP 429); applying backoff of {}ms on attempt {}", backoffMs, currentAttempt);
                return RecoveryDecision.backoff(
                        "Rate limit exceeded (HTTP 429); backing off before retry",
                        backoffMs,
                        auditData
                );
            } else {
                log.warn("Rate limit retry budget exhausted (attempt {}/{}); replanning", currentAttempt, maxAttempts);
                return RecoveryDecision.replan(
                        "Rate limit retry budget exhausted after " + currentAttempt + " attempts",
                        auditData
                );
            }
        }

        // 4. Transient Service Unavailable: HTTP 503
        if (httpStatus != null && httpStatus == 503) {
            if (currentAttempt < maxAttempts) {
                long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
                auditData.put("backoffMs", backoffMs);
                log.info("Service unavailable (HTTP 503); retrying with backoff of {}ms on attempt {}", backoffMs, currentAttempt);
                return RecoveryDecision.retry(
                        "Transient upstream service unavailable (HTTP 503)",
                        backoffMs,
                        auditData
                );
            } else if (context.getFallbackToolName() != null && !context.getFallbackToolName().isBlank()) {
                log.info("HTTP 503 retry exhausted; engaging fallback tool: {}", context.getFallbackToolName());
                return RecoveryDecision.fallbackTool(
                        context.getFallbackToolName(),
                        "Primary tool failed with 503; switching to fallback tool",
                        auditData
                );
            } else {
                log.warn("HTTP 503 retry budget exhausted (attempt {}/{}); replanning", currentAttempt, maxAttempts);
                return RecoveryDecision.replan(
                        "Service unavailable (HTTP 503) retries exhausted after " + currentAttempt + " attempts",
                        auditData
                );
            }
        }

        // 5. Server Errors: HTTP 500 / 502 / 504 or classification HTTP_5XX
        if (classification == ErrorClassification.HTTP_5XX || (httpStatus != null && httpStatus >= 500 && httpStatus < 600)) {
            if (currentAttempt < maxAttempts) {
                long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
                auditData.put("backoffMs", backoffMs);
                log.info("Upstream 5XX server error (HTTP {}); retrying on attempt {}", httpStatus, currentAttempt);
                return RecoveryDecision.retry(
                        "Transient upstream server error (HTTP " + (httpStatus != null ? httpStatus : "5XX") + ")",
                        backoffMs,
                        auditData
                );
            } else {
                log.warn("5XX server error retries exhausted; replanning");
                return RecoveryDecision.replan(
                        "Upstream 5XX server error retries exhausted after " + currentAttempt + " attempts",
                        auditData
                );
            }
        }

        // 6. Timeouts & Network Errors
        if (classification == ErrorClassification.TIMEOUT || classification == ErrorClassification.NETWORK_ERROR) {
            if (currentAttempt < maxAttempts) {
                long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
                auditData.put("backoffMs", backoffMs);
                log.info("Request timeout/network error; retrying with backoff {}ms on attempt {}", backoffMs, currentAttempt);
                return RecoveryDecision.retry(
                        "Transient timeout or network error",
                        backoffMs,
                        auditData
                );
            } else if (context.getFallbackToolName() != null && !context.getFallbackToolName().isBlank()) {
                return RecoveryDecision.fallbackTool(
                        context.getFallbackToolName(),
                        "Timeout retries exhausted; switching to fallback tool",
                        auditData
                );
            } else {
                return RecoveryDecision.replan(
                        "Timeout or network error retries exhausted after " + currentAttempt + " attempts",
                        auditData
                );
            }
        }

        // 7. Malformed responses
        if (classification == ErrorClassification.MALFORMED_RESPONSE) {
            if (currentAttempt < maxAttempts) {
                long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
                auditData.put("backoffMs", backoffMs);
                log.info("Malformed response payload; retrying on attempt {}", currentAttempt);
                return RecoveryDecision.retry(
                        "Malformed or unparseable response payload received",
                        backoffMs,
                        auditData
                );
            } else {
                return RecoveryDecision.replan(
                        "Malformed response retries exhausted after " + currentAttempt + " attempts",
                        auditData
                );
            }
        }

        // 8. Default fallback for other errors (unknown / unhandled agent failure)
        if (currentAttempt < maxAttempts) {
            long backoffMs = backoffStrategy.calculateBackoffMs(currentAttempt);
            auditData.put("backoffMs", backoffMs);
            return RecoveryDecision.retry("General error recovery attempt", backoffMs, auditData);
        }

        return RecoveryDecision.replan(
                "Recovery retry budget exhausted after " + currentAttempt + " attempts",
                auditData
        );
    }
}
