package com.incidentmind.planner.model;

import com.incidentmind.task.entity.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannedTask {

    private String taskType;
    private String title;
    private String description;
    private TaskPriority priority;
    private String requiredCapability;
    private UUID parentTaskId;
    private String assignedAgentType;
    @Builder.Default
    private ToolRequirement toolRequirement = ToolRequirement.NO_TOOL_REQUIRED;
    private String suggestedToolName;
    private String toolSelectionReason;
}
