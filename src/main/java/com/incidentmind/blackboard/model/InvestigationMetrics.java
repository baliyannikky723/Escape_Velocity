package com.incidentmind.blackboard.model;

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
public class InvestigationMetrics {

    @Builder.Default
    private long totalDurationMs = 0;
    @Builder.Default
    private int agentRunsCount = 0;
    @Builder.Default
    private int toolCallsCount = 0;
    @Builder.Default
    private int recoveryAttemptsCount = 0;
    @Builder.Default
    private int criticEvaluationsCount = 0;
    @Builder.Default
    private int criticRejectionsCount = 0;
    @Builder.Default
    private int criticAcceptancesCount = 0;
    @Builder.Default
    private int tasksCreatedCount = 0;
    @Builder.Default
    private int tasksCompletedCount = 0;
    @Builder.Default
    private int tasksFailedCount = 0;
    @Builder.Default
    private int tasksRejectedCount = 0;
    @Builder.Default
    private int tasksSkippedCount = 0;
    @Builder.Default
    private int tasksBlockedCount = 0;

    public double getTaskCompletionRate() {
        if (tasksCreatedCount == 0) return 0.0;
        return (double) tasksCompletedCount / tasksCreatedCount;
    }
}
