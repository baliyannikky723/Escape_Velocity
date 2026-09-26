package com.incidentmind.tool.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitHubToolAdapter {

    public static final String TOOL_TYPE_GITHUB = "EXTERNAL_API";

    private final RestClient restClient;
    private final GitHubProperties properties;
    private final ObjectMapper objectMapper;

    public GitHubToolAdapter(GitHubProperties properties, ObjectMapper objectMapper) {
        this(buildDefaultRestClient(properties), properties, objectMapper);
    }

    public GitHubToolAdapter(RestClient restClient, GitHubProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildDefaultRestClient(GitHubProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "IncidentMind-ToolGateway/1.0");

        if (properties.getToken() != null && !properties.getToken().trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().trim());
        }

        return builder.build();
    }

    public ToolResult getRepository(String toolName, String owner, String repo) {
        String path = String.format("/repos/%s/%s", owner, repo);
        return executeGet(toolName, path);
    }

    public ToolResult getRecentCommits(String toolName, String owner, String repo, Integer perPage) {
        int limit = (perPage != null && perPage > 0) ? Math.min(perPage, 100) : 10;
        String path = String.format("/repos/%s/%s/commits?per_page=%d", owner, repo, limit);
        return executeGet(toolName, path);
    }

    public ToolResult getPullRequests(String toolName, String owner, String repo, String state, Integer perPage) {
        String prState = (state != null && !state.trim().isEmpty()) ? state.trim().toLowerCase() : "all";
        int limit = (perPage != null && perPage > 0) ? Math.min(perPage, 100) : 10;
        String path = String.format("/repos/%s/%s/pulls?state=%s&per_page=%d", owner, repo, prState, limit);
        return executeGet(toolName, path);
    }

    private ToolResult executeGet(String toolName, String uriPath) {
        long startTime = System.currentTimeMillis();

        try {
            return restClient.get()
                    .uri(uriPath)
                    .exchange((request, response) -> handleResponse(toolName, response, startTime));
        } catch (ResourceAccessException ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("GitHub API resource access exception on {}: {}", uriPath, ex.getMessage());

            boolean isTimeout = ex.getCause() instanceof SocketTimeoutException
                    || ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout");

            if (isTimeout) {
                return ToolResult.failure(
                        toolName,
                        TOOL_TYPE_GITHUB,
                        ToolCallStatus.TIMEOUT,
                        null,
                        "TIMEOUT",
                        "Request to GitHub timed out: " + ex.getMessage(),
                        ErrorClassification.TIMEOUT,
                        Map.of("error", "TIMEOUT", "details", ex.getMessage()),
                        Map.of(),
                        durationMs
                );
            } else {
                return ToolResult.failure(
                        toolName,
                        TOOL_TYPE_GITHUB,
                        ToolCallStatus.FAILED,
                        null,
                        "NETWORK_ERROR",
                        "Network error connecting to GitHub: " + ex.getMessage(),
                        ErrorClassification.NETWORK_ERROR,
                        Map.of("error", "NETWORK_ERROR", "details", ex.getMessage()),
                        Map.of(),
                        durationMs
                );
            }
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Unexpected error executing GitHub tool call on {}", uriPath, ex);
            return ToolResult.failure(
                    toolName,
                    TOOL_TYPE_GITHUB,
                    ToolCallStatus.FAILED,
                    null,
                    "UNKNOWN_ERROR",
                    "Unexpected error: " + ex.getMessage(),
                    ErrorClassification.UNKNOWN,
                    Map.of("error", "UNKNOWN_ERROR", "details", ex.getMessage()),
                    Map.of(),
                    durationMs
            );
        }
    }

    private ToolResult handleResponse(String toolName, ClientHttpResponse response, long startTime) throws IOException {
        long durationMs = System.currentTimeMillis() - startTime;
        HttpStatusCode statusCode = response.getStatusCode();
        int httpStatus = statusCode.value();

        Map<String, String> rateLimits = extractRateLimitHeaders(response.getHeaders());
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

        if (statusCode.is2xxSuccessful()) {
            try {
                Map<String, Object> payload;
                if (body.trim().startsWith("[")) {
                    List<Map<String, Object>> listPayload = objectMapper.readValue(body, new TypeReference<>() {});
                    payload = Map.of("items", listPayload, "count", listPayload.size());
                } else if (body.trim().startsWith("{")) {
                    payload = objectMapper.readValue(body, new TypeReference<>() {});
                } else {
                    return ToolResult.failure(
                            toolName,
                            TOOL_TYPE_GITHUB,
                            ToolCallStatus.MALFORMED_RESPONSE,
                            httpStatus,
                            "MALFORMED_RESPONSE",
                            "Response is neither a valid JSON object nor array",
                            ErrorClassification.MALFORMED_RESPONSE,
                            Map.of("rawBody", body),
                            rateLimits,
                            durationMs
                    );
                }

                return ToolResult.success(toolName, TOOL_TYPE_GITHUB, httpStatus, payload, rateLimits, durationMs);
            } catch (Exception ex) {
                log.warn("Failed to parse 2xx response from GitHub for tool {}: {}", toolName, ex.getMessage());
                return ToolResult.failure(
                        toolName,
                        TOOL_TYPE_GITHUB,
                        ToolCallStatus.MALFORMED_RESPONSE,
                        httpStatus,
                        "MALFORMED_RESPONSE",
                        "Failed to parse GitHub JSON response: " + ex.getMessage(),
                        ErrorClassification.MALFORMED_RESPONSE,
                        Map.of("rawBody", body, "parseError", ex.getMessage()),
                        rateLimits,
                        durationMs
                );
            }
        }

        // Error handling for 4xx and 5xx
        Map<String, Object> errorPayload;
        try {
            errorPayload = objectMapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            errorPayload = Map.of("rawBody", body);
        }

        String errorCode = switch (httpStatus) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 422 -> "VALIDATION_FAILED";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            case 500 -> "GITHUB_INTERNAL_ERROR";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "SERVICE_UNAVAILABLE";
            case 504 -> "GATEWAY_TIMEOUT";
            default -> "HTTP_" + httpStatus;
        };

        String message = errorPayload.containsKey("message")
                ? errorPayload.get("message").toString()
                : "GitHub API returned status " + httpStatus;

        ErrorClassification classification = ErrorClassification.fromHttpStatus(httpStatus);

        return ToolResult.failure(
                toolName,
                TOOL_TYPE_GITHUB,
                ToolCallStatus.FAILED,
                httpStatus,
                errorCode,
                message,
                classification,
                errorPayload,
                rateLimits,
                durationMs
        );
    }

    private Map<String, String> extractRateLimitHeaders(HttpHeaders headers) {
        Map<String, String> rateLimits = new HashMap<>();
        if (headers.containsKey("X-RateLimit-Limit")) {
            rateLimits.put("X-RateLimit-Limit", headers.getFirst("X-RateLimit-Limit"));
        }
        if (headers.containsKey("X-RateLimit-Remaining")) {
            rateLimits.put("X-RateLimit-Remaining", headers.getFirst("X-RateLimit-Remaining"));
        }
        if (headers.containsKey("X-RateLimit-Reset")) {
            rateLimits.put("X-RateLimit-Reset", headers.getFirst("X-RateLimit-Reset"));
        }
        return rateLimits;
    }
}
