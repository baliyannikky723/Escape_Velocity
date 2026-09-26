package com.incidentmind.tool.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubToolAdapterTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private GitHubToolAdapter gitHubToolAdapter;
    private GitHubProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setApiBaseUrl("https://api.github.com");
        properties.setToken("test-gh-token");

        restClientBuilder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken());

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        gitHubToolAdapter = new GitHubToolAdapter(restClientBuilder.build(), properties, objectMapper);
    }

    @Test
    @DisplayName("GitHub Adapter: 200 OK getRepository parses response and extracts rate limit headers")
    void getRepository_Success200() {
        String jsonResponse = """
                {
                    "name": "Hello-World",
                    "full_name": "octocat/Hello-World",
                    "description": "My first repo",
                    "default_branch": "master",
                    "stargazers_count": 80
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "60");
        headers.set("X-RateLimit-Remaining", "58");
        headers.set("X-RateLimit-Reset", "1700000000");

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-gh-token"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON).headers(headers));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "Hello-World");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getResponsePayload().get("name")).isEqualTo("Hello-World");
        assertThat(result.getResponsePayload().get("full_name")).isEqualTo("octocat/Hello-World");
        assertThat(result.getRateLimitInfo().get("X-RateLimit-Remaining")).isEqualTo("58");

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 200 OK getRecentCommits parses list payload")
    void getRecentCommits_Success200() {
        String jsonResponse = """
                [
                    {
                        "sha": "6dcb09b5b57875f334f61aebed695e2e4193db5e",
                        "commit": {
                            "message": "Fix checkout payment NPE bug"
                        }
                    }
                ]
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/commits?per_page=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRecentCommits("github.get_recent_commits", "octocat", "Hello-World", 5);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(result.getResponsePayload().get("count")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getResponsePayload().get("items");
        assertThat(items.get(0).get("sha")).isEqualTo("6dcb09b5b57875f334f61aebed695e2e4193db5e");

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 404 Not Found returns FAILED with HTTP_4XX classification")
    void getRepository_404NotFound() {
        String errorJson = """
                {
                    "message": "Not Found",
                    "documentation_url": "https://docs.github.com/rest/repos/repos#get-a-repository"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/nonexistent/repo-xyz"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "nonexistent", "repo-xyz");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(result.getHttpStatus()).isEqualTo(404);
        assertThat(result.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_4XX);
        assertThat(result.getErrorMessage()).isEqualTo("Not Found");

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 401 Unauthorized returns FAILED with UNAUTHORIZED code")
    void getRepository_401Unauthorized() {
        String errorJson = """
                {
                    "message": "Bad credentials"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/secret-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "secret-repo");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(401);
        assertThat(result.getErrorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_4XX);

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 403 Forbidden returns FORBIDDEN code")
    void getRepository_403Forbidden() {
        String errorJson = """
                {
                    "message": "Resource not accessible by integration"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/forbidden-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "forbidden-repo");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(403);
        assertThat(result.getErrorCode()).isEqualTo("FORBIDDEN");

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 429 Rate Limit returns RATE_LIMIT_EXCEEDED")
    void getRepository_429RateLimit() {
        String errorJson = """
                {
                    "message": "API rate limit exceeded for user"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "Hello-World");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(429);
        assertThat(result.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(result.getRateLimitInfo().get("X-RateLimit-Remaining")).isEqualTo("0");

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 500 Internal Server Error returns HTTP_5XX classification")
    void getRepository_500InternalError() {
        String errorJson = """
                {
                    "message": "Internal Server Error"
                }
                """;

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "Hello-World");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(500);
        assertThat(result.getErrorCode()).isEqualTo("GITHUB_INTERNAL_ERROR");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_5XX);

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: 503 Service Unavailable returns HTTP_5XX classification")
    void getRepository_503ServiceUnavailable() {
        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service Unavailable"));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "Hello-World");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(503);
        assertThat(result.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.HTTP_5XX);

        mockServer.verify();
    }

    @Test
    @DisplayName("GitHub Adapter: Malformed JSON response returns MALFORMED_RESPONSE status")
    void getRepository_MalformedJson_ReturnsMalformedResponse() {
        String invalidJson = "<!DOCTYPE html><html><body>Error 500 HTML instead of JSON</body></html>";

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(invalidJson, MediaType.TEXT_HTML));

        ToolResult result = gitHubToolAdapter.getRepository("github.get_repository", "octocat", "Hello-World");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ToolCallStatus.MALFORMED_RESPONSE);
        assertThat(result.getErrorCode()).isEqualTo("MALFORMED_RESPONSE");
        assertThat(result.getErrorClassification()).isEqualTo(ErrorClassification.MALFORMED_RESPONSE);

        mockServer.verify();
    }
}
