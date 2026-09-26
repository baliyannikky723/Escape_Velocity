package com.incidentmind.recovery.policy;

import com.incidentmind.recovery.config.RecoveryProperties;
import com.incidentmind.recovery.entity.RecoveryType;
import com.incidentmind.recovery.model.RecoveryContext;
import com.incidentmind.recovery.model.RecoveryDecision;
import com.incidentmind.tool.model.ErrorClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryPolicyTest {

    private RecoveryProperties properties;
    private ExponentialBackoffStrategy backoffStrategy;
    private DefaultRecoveryPolicy recoveryPolicy;

    @BeforeEach
    void setUp() {
        properties = new RecoveryProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        properties.setInitialBackoffMs(100);
        properties.setMaxBackoffMs(1000);
        properties.setBackoffMultiplier(2.0);
        properties.setExponentialBackoff(true);

        backoffStrategy = new ExponentialBackoffStrategy(properties, millis -> {}); // no-op sleeper for tests
        recoveryPolicy = new DefaultRecoveryPolicy(properties, backoffStrategy);
    }

    @Test
    @DisplayName("HTTP 500 triggers RETRY decision when under max attempts")
    void evaluate_Http500_ReturnsRetry() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(500)
                .errorClassification(ErrorClassification.HTTP_5XX)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.RETRY);
        assertThat(decision.isShouldRecover()).isTrue();
        assertThat(decision.getBackoffMs()).isEqualTo(100);
    }

    @Test
    @DisplayName("HTTP 503 triggers RETRY/BACKOFF decision with exponential backoff")
    void evaluate_Http503_ReturnsRetryWithExponentialBackoff() {
        RecoveryContext contextAttempt2 = RecoveryContext.builder()
                .httpStatus(503)
                .errorClassification(ErrorClassification.HTTP_5XX)
                .attemptNumber(2)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(contextAttempt2);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.RETRY);
        assertThat(decision.isShouldRecover()).isTrue();
        assertThat(decision.getBackoffMs()).isEqualTo(200); // 100 * 2^(2-1) = 200
    }

    @Test
    @DisplayName("HTTP 429 triggers BACKOFF decision")
    void evaluate_Http429_ReturnsBackoff() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(429)
                .errorClassification(ErrorClassification.HTTP_4XX)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.BACKOFF);
        assertThat(decision.isShouldRecover()).isTrue();
    }

    @Test
    @DisplayName("TIMEOUT triggers RETRY decision")
    void evaluate_Timeout_ReturnsRetry() {
        RecoveryContext context = RecoveryContext.builder()
                .errorClassification(ErrorClassification.TIMEOUT)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.RETRY);
        assertThat(decision.isShouldRecover()).isTrue();
    }

    @Test
    @DisplayName("MALFORMED_RESPONSE triggers RETRY decision")
    void evaluate_MalformedResponse_ReturnsRetry() {
        RecoveryContext context = RecoveryContext.builder()
                .errorClassification(ErrorClassification.MALFORMED_RESPONSE)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.RETRY);
        assertThat(decision.isShouldRecover()).isTrue();
    }

    @Test
    @DisplayName("HTTP 403 triggers ABORT decision (never retried)")
    void evaluate_Http403_ReturnsAbort() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(403)
                .errorClassification(ErrorClassification.HTTP_4XX)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.ABORT);
        assertThat(decision.isShouldRecover()).isFalse();
    }

    @Test
    @DisplayName("HTTP 401 triggers ABORT decision (never retried)")
    void evaluate_Http401_ReturnsAbort() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(401)
                .errorClassification(ErrorClassification.HTTP_4XX)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.ABORT);
        assertThat(decision.isShouldRecover()).isFalse();
    }

    @Test
    @DisplayName("HTTP 404 triggers REPLAN decision")
    void evaluate_Http404_ReturnsReplan() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(404)
                .errorClassification(ErrorClassification.HTTP_4XX)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.REPLAN);
        assertThat(decision.isShouldRecover()).isFalse();
    }

    @Test
    @DisplayName("VALIDATION_ERROR triggers ABORT decision")
    void evaluate_ValidationError_ReturnsAbort() {
        RecoveryContext context = RecoveryContext.builder()
                .errorClassification(ErrorClassification.VALIDATION_ERROR)
                .attemptNumber(1)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.ABORT);
        assertThat(decision.isShouldRecover()).isFalse();
    }

    @Test
    @DisplayName("Retry limit exhaustion triggers REPLAN decision")
    void evaluate_RetriesExhausted_ReturnsReplan() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(503)
                .errorClassification(ErrorClassification.HTTP_5XX)
                .attemptNumber(3)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.REPLAN);
        assertThat(decision.isShouldRecover()).isFalse();
    }

    @Test
    @DisplayName("Retry limit exhaustion with fallback tool triggers FALLBACK_TOOL decision")
    void evaluate_RetriesExhausted_WithFallback_ReturnsFallbackTool() {
        RecoveryContext context = RecoveryContext.builder()
                .httpStatus(503)
                .errorClassification(ErrorClassification.HTTP_5XX)
                .fallbackToolName("github.get_pull_requests")
                .attemptNumber(3)
                .maxAttempts(3)
                .build();

        RecoveryDecision decision = recoveryPolicy.evaluate(context);

        assertThat(decision.getRecoveryType()).isEqualTo(RecoveryType.FALLBACK_TOOL);
        assertThat(decision.getTargetToolName()).isEqualTo("github.get_pull_requests");
        assertThat(decision.isShouldRecover()).isTrue();
    }
}
