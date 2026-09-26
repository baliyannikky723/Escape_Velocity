package com.incidentmind.audit.service;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditService {

    AuditEvent recordEvent(UUID investigationId,
                           UUID taskId,
                           UUID agentRunId,
                           AuditEventType eventType,
                           ActorType actorType,
                           String actorId,
                           Map<String, Object> eventData,
                           UUID correlationId);

    List<AuditEvent> getAuditEventsByInvestigationId(UUID investigationId);
}
