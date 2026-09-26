package com.incidentmind.audit.service;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional
    public AuditEvent recordEvent(UUID investigationId,
                                  UUID taskId,
                                  UUID agentRunId,
                                  AuditEventType eventType,
                                  ActorType actorType,
                                  String actorId,
                                  Map<String, Object> eventData,
                                  UUID correlationId) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .investigationId(investigationId)
                .taskId(taskId)
                .agentRunId(agentRunId)
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .eventData(eventData)
                .correlationId(correlationId)
                .occurredAt(Instant.now())
                .build();

        AuditEvent saved = auditEventRepository.save(event);
        log.debug("Recorded audit event: type={}, correlationId={}, investigationId={}",
                eventType, correlationId, investigationId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> getAuditEventsByInvestigationId(UUID investigationId) {
        return auditEventRepository.findByInvestigationIdOrderByOccurredAtAsc(investigationId);
    }
}
