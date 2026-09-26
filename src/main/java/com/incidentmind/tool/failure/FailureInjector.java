package com.incidentmind.tool.failure;

import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class FailureInjector {

    private final FailureInjectionProperties properties;

    public FailureInjector(FailureInjectionProperties properties) {
        this.properties = properties;
    }

    public Optional<ToolResult> maybeInjectFailure(ToolRequest request, String toolType) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        InjectedFailureMode mode = properties.getFailureModeForTool(request.getToolName());
        if (mode == InjectedFailureMode.NONE) {
            return Optional.empty();
        }

        log.warn("INJECTING TEST FAILURE: mode={} for tool={}", mode, request.getToolName());

        ToolResult injectedResult = switch (mode) {
            case HTTP_500 -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.FAILED,
                    500,
                    "HTTP_500",
                    "Simulated internal server error (injected failure)",
                    ErrorClassification.HTTP_5XX,
                    Map.of("error", "Internal Server Error", "injected", true),
                    Map.of("X-RateLimit-Limit", "60", "X-RateLimit-Remaining", "59"),
                    150L
            );
            case HTTP_503 -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.FAILED,
                    503,
                    "HTTP_503",
                    "Simulated service unavailable (injected failure)",
                    ErrorClassification.HTTP_5XX,
                    Map.of("error", "Service Unavailable", "injected", true),
                    Map.of(),
                    120L
            );
            case HTTP_403 -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.FAILED,
                    403,
                    "HTTP_403",
                    "Simulated forbidden response (injected failure)",
                    ErrorClassification.HTTP_4XX,
                    Map.of("error", "Forbidden", "injected", true),
                    Map.of(),
                    80L
            );
            case HTTP_429 -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.FAILED,
                    429,
                    "RATE_LIMIT_EXCEEDED",
                    "Simulated rate limit exceeded (injected failure)",
                    ErrorClassification.HTTP_4XX,
                    Map.of("error", "API rate limit exceeded", "injected", true),
                    Map.of("X-RateLimit-Limit", "60", "X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "1700000000"),
                    90L
            );
            case TIMEOUT -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.TIMEOUT,
                    null,
                    "TIMEOUT",
                    "Simulated request timeout after configured threshold (injected failure)",
                    ErrorClassification.TIMEOUT,
                    Map.of("timeout_type", "READ_TIMEOUT", "injected", true),
                    Map.of(),
                    2000L
            );
            case MALFORMED_RESPONSE -> ToolResult.failure(
                    request.getToolName(),
                    toolType,
                    ToolCallStatus.MALFORMED_RESPONSE,
                    200,
                    "MALFORMED_RESPONSE",
                    "Simulated malformed or invalid response payload (injected failure)",
                    ErrorClassification.MALFORMED_RESPONSE,
                    Map.of("raw", "<!DOCTYPE html><html><body>Error</body></html>", "injected", true),
                    Map.of(),
                    100L
            );
            case NONE -> null;
        };

        return Optional.ofNullable(injectedResult);
    }
}
