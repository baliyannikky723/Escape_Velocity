package com.incidentmind.investigation.dto;

import jakarta.validation.constraints.Min;
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

    private String objective;

    @Min(value = 1, message = "maxTasks must be at least 1")
    private Integer maxTasks;

    @Min(value = 0, message = "maxRetriesPerTask cannot be negative")
    private Integer maxRetriesPerTask;

    @Min(value = 1, message = "maxRuntimeSeconds must be at least 1")
    private Integer maxRuntimeSeconds;
}
