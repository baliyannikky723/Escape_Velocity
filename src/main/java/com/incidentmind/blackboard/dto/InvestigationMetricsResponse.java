package com.incidentmind.blackboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.blackboard.model.InvestigationMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvestigationMetricsResponse {

    private long totalDurationMs;
    private int agentRunsCount;
    private int toolCallsCount;
    private int recoveryAttemptsCount;
    private int criticEvaluationsCount;
    private int criticRejectionsCount;
    private int criticAcceptancesCount;
    private int tasksCreatedCount;
    private int tasksCompletedCount;
    private int tasksFailedCount;
    private int tasksRejectedCount;
    private int tasksSkippedCount;
    private int tasksBlockedCount;
    private double taskCompletionRate;

    public static InvestigationMetricsResponse fromModel(InvestigationMetrics metrics) {
        if (metrics == null) return null;
        return InvestigationMetricsResponse.builder()
                .totalDurationMs(metrics.getTotalDurationMs())
                .agentRunsCount(metrics.getAgentRunsCount())
                .toolCallsCount(metrics.getToolCallsCount())
                .recoveryAttemptsCount(metrics.getRecoveryAttemptsCount())
                .criticEvaluationsCount(metrics.getCriticEvaluationsCount())
                .criticRejectionsCount(metrics.getCriticRejectionsCount())
                .criticAcceptancesCount(metrics.getCriticAcceptancesCount())
                .tasksCreatedCount(metrics.getTasksCreatedCount())
                .tasksCompletedCount(metrics.getTasksCompletedCount())
                .tasksFailedCount(metrics.getTasksFailedCount())
                .tasksRejectedCount(metrics.getTasksRejectedCount())
                .tasksSkippedCount(metrics.getTasksSkippedCount())
                .tasksBlockedCount(metrics.getTasksBlockedCount())
                .taskCompletionRate(metrics.getTaskCompletionRate())
                .build();
    }
}
