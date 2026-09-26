package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.critic.model.CriticDecision;
import com.incidentmind.task.entity.TaskPriority;
import com.incidentmind.task.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskGraphNode {

    private UUID taskId;
    private String taskType;
    private String title;
    private String description;
    private String assignedAgent;
    private TaskStatus status;
    private TaskPriority priority;
    private UUID parentTaskId;
    private Instant createdAt;
    private Instant completedAt;
    private CriticDecision criticDecision;
    private int retryCount;
}
