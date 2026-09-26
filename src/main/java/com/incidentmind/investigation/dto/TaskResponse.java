package com.incidentmind.investigation.dto;

import com.incidentmind.task.entity.InvestigationTask;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private UUID id;
    private UUID investigationId;
    private UUID parentTaskId;
    private String taskType;
    private String assignedAgentType;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    public static TaskResponse fromEntity(InvestigationTask task) {
        if (task == null) {
            return null;
        }
        return TaskResponse.builder()
                .id(task.getId())
                .investigationId(task.getInvestigationId())
                .parentTaskId(task.getParentTaskId())
                .taskType(task.getTaskType())
                .assignedAgentType(task.getAssignedAgentType())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .attemptCount(task.getAttemptCount())
                .maxAttempts(task.getMaxAttempts())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
