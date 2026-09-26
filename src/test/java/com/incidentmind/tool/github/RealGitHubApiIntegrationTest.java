package com.incidentmind.tool.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("real-api")
class RealGitHubApiIntegrationTest {

    private final GitHubProperties properties = new GitHubProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Real GitHub API: invoke github.get_repository for public repo octocat/Hello-World")
    void getRepository_RealGitHubApi_Invocation() {
        GitHubToolAdapter adapter = new GitHubToolAdapter(properties, objectMapper);

        ToolResult result = adapter.getRepository("github.get_repository", "octocat", "Hello-World");

        // When internet is available, verify live response shape from GitHub
        if (result.isSuccess()) {
            assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
            assertThat(result.getHttpStatus()).isEqualTo(200);
            assertThat(result.getResponsePayload()).isNotNull();
            assertThat(result.getResponsePayload().get("name")).isEqualTo("Hello-World");
            assertThat(result.getResponsePayload().get("full_name")).isEqualTo("octocat/Hello-World");
            assertThat(result.getRateLimitInfo()).isNotNull();
            assertThat(result.getRateLimitInfo()).containsKey("X-RateLimit-Limit");
        } else {
            // In offline/rate-limited environments, ensure error was classified properly without unhandled exception
            assertThat(result.getStatus()).isIn(ToolCallStatus.FAILED, ToolCallStatus.TIMEOUT);
            assertThat(result.getErrorClassification()).isNotNull();
        }
    }
}
