package com.incidentmind.incident.dto;

import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
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
public class IncidentResponse {

    private UUID id;
    private String incidentKey;
    private String title;
    private String description;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private String serviceName;
    private String environment;
    private Instant startedAt;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static IncidentResponse fromEntity(Incident incident) {
        if (incident == null) {
            return null;
        }
        return IncidentResponse.builder()
                .id(incident.getId())
                .incidentKey(incident.getIncidentKey())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .serviceName(incident.getServiceName())
                .environment(incident.getEnvironment())
                .startedAt(incident.getStartedAt())
                .resolvedAt(incident.getResolvedAt())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }
}
