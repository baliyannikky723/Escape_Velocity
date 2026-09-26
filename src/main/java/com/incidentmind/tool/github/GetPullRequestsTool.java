package com.incidentmind.tool.github;

import com.incidentmind.tool.core.Tool;
import com.incidentmind.tool.entity.ToolCallStatus;
import com.incidentmind.tool.model.ErrorClassification;
import com.incidentmind.tool.model.ToolExecutionContext;
import com.incidentmind.tool.model.ToolMetadata;
import com.incidentmind.tool.model.ToolRequest;
import com.incidentmind.tool.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetPullRequestsTool implements Tool {

    public static final String TOOL_NAME = "github.get_pull_requests";

    private final GitHubToolAdapter gitHubToolAdapter;

    public GetPullRequestsTool(GitHubToolAdapter gitHubToolAdapter) {
        this.gitHubToolAdapter = gitHubToolAdapter;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getType() {
        return GitHubToolAdapter.TOOL_TYPE_GITHUB;
    }

    @Override
    public String getDescription() {
        return "Retrieves pull requests from a GitHub repository";
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder()
                .name(TOOL_NAME)
                .type(getType())
                .description(getDescription())
                .requiredParameters(List.of("owner", "repository"))
                .parameterDescriptions(Map.of(
                        "owner", "GitHub repository owner/organization",
                        "repository", "GitHub repository name",
                        "state", "Optional state: 'open', 'closed', or 'all' (default: 'all')",
                        "perPage", "Optional number of PRs to retrieve (default: 10, max: 100)"
                ))
                .build();
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) {
        String owner = request.getStringParameter("owner");
        String repository = request.getStringParameter("repository");
        String state = request.getStringParameter("state");
        Integer perPage = request.getIntegerParameter("perPage", 10);

        if (owner == null || owner.isBlank() || repository == null || repository.isBlank()) {
            return ToolResult.failure(
                    TOOL_NAME,
                    getType(),
                    ToolCallStatus.FAILED,
                    400,
                    "VALIDATION_ERROR",
                    "Both 'owner' and 'repository' parameters are required for " + TOOL_NAME,
                    ErrorClassification.VALIDATION_ERROR,
                    Map.of("error", "Missing required parameters"),
                    Map.of(),
                    0L
            );
        }

        return gitHubToolAdapter.getPullRequests(TOOL_NAME, owner.trim(), repository.trim(), state, perPage);
    }
}
