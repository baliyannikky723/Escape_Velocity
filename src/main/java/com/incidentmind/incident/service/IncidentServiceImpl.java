package com.incidentmind.incident.service;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.common.filter.CorrelationContext;
import com.incidentmind.incident.dto.CreateIncidentRequest;
import com.incidentmind.incident.dto.IncidentResponse;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;

    public IncidentServiceImpl(IncidentRepository incidentRepository, AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Long seqVal = incidentRepository.getNextIncidentSequenceValue();
        String incidentKey = String.format("INC-%06d", seqVal);

        Instant now = Instant.now();
        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .incidentKey(incidentKey)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .severity(request.getSeverity())
                .status(IncidentStatus.OPEN)
                .serviceName(request.getServiceName().trim())
                .environment(request.getEnvironment().trim())
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Incident savedIncident = incidentRepository.save(incident);
        log.info("Created incident with id={}, incidentKey={}, severity={}",
                savedIncident.getId(), savedIncident.getIncidentKey(), savedIncident.getSeverity());

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("incidentId", savedIncident.getId().toString());
        auditData.put("incidentKey", savedIncident.getIncidentKey());
        auditData.put("title", savedIncident.getTitle());
        auditData.put("severity", savedIncident.getSeverity().name());
        auditData.put("serviceName", savedIncident.getServiceName());
        auditData.put("environment", savedIncident.getEnvironment());

        auditService.recordEvent(
                null,
                null,
                null,
                AuditEventType.INCIDENT_CREATED,
                ActorType.USER,
                "api-user",
                auditData,
                CorrelationContext.getCorrelationIdAsUuid()
        );

        return IncidentResponse.fromEntity(savedIncident);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(UUID id) {
        Incident incident = getIncidentEntity(id);
        return IncidentResponse.fromEntity(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(IncidentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Incident getIncidentEntity(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }
}
