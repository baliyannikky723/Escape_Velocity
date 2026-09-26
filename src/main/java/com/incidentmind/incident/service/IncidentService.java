package com.incidentmind.incident.service;

import com.incidentmind.incident.dto.CreateIncidentRequest;
import com.incidentmind.incident.dto.IncidentResponse;
import com.incidentmind.incident.entity.Incident;

import java.util.UUID;

public interface IncidentService {

    IncidentResponse createIncident(CreateIncidentRequest request);

    IncidentResponse getIncidentById(UUID id);

    java.util.List<IncidentResponse> getAllIncidents();

    Incident getIncidentEntity(UUID id);
}
