package com.incidentmind.investigation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateInvestigationRequest {

    @NotBlank(message = "Objective is required")
    private String objective;

    @NotNull(message = "maxTasks is required")
    @Min(value = 1, message = "maxTasks must be at least 1")
    private Integer maxTasks;

    @NotNull(message = "maxRetriesPerTask is required")
    @Min(value = 0, message = "maxRetriesPerTask cannot be negative")
    private Integer maxRetriesPerTask;

    @NotNull(message = "maxRuntimeSeconds is required")
    @Min(value = 1, message = "maxRuntimeSeconds must be at least 1")
    private Integer maxRuntimeSeconds;
}
