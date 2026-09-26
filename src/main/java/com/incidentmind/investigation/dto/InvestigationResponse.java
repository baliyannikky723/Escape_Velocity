package com.incidentmind.investigation.dto;

import com.incidentmind.investigation.entity.Investigation;
import com.incidentmind.investigation.entity.InvestigationStatus;
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
public class InvestigationResponse {

    private UUID id;
    private UUID incidentId;
    private InvestigationStatus status;
    private String objective;
    private Instant startedAt;
    private Instant completedAt;
    private Integer maxTasks;
    private Integer maxRetriesPerTask;
    private Integer maxRuntimeSeconds;
    private Instant createdAt;
    private Instant updatedAt;

    public static InvestigationResponse fromEntity(Investigation investigation) {
        if (investigation == null) {
            return null;
        }
        return InvestigationResponse.builder()
                .id(investigation.getId())
                .incidentId(investigation.getIncidentId())
                .status(investigation.getStatus())
                .objective(investigation.getObjective())
                .startedAt(investigation.getStartedAt())
                .completedAt(investigation.getCompletedAt())
                .maxTasks(investigation.getMaxTasks())
                .maxRetriesPerTask(investigation.getMaxRetriesPerTask())
                .maxRuntimeSeconds(investigation.getMaxRuntimeSeconds())
                .createdAt(investigation.getCreatedAt())
                .updatedAt(investigation.getUpdatedAt())
                .build();
    }
}
