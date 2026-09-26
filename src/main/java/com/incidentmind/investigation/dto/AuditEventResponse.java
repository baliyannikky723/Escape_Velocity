package com.incidentmind.investigation.dto;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEvent;
import com.incidentmind.audit.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponse {

    private UUID id;
    private UUID investigationId;
    private UUID taskId;
    private UUID agentRunId;
    private AuditEventType eventType;
    private ActorType actorType;
    private String actorId;
    private Map<String, Object> eventData;
    private UUID correlationId;
    private Instant occurredAt;

    public static AuditEventResponse fromEntity(AuditEvent auditEvent) {
        if (auditEvent == null) {
            return null;
        }
        return AuditEventResponse.builder()
                .id(auditEvent.getId())
                .investigationId(auditEvent.getInvestigationId())
                .taskId(auditEvent.getTaskId())
                .agentRunId(auditEvent.getAgentRunId())
                .eventType(auditEvent.getEventType())
                .actorType(auditEvent.getActorType())
                .actorId(auditEvent.getActorId())
                .eventData(auditEvent.getEventData())
                .correlationId(auditEvent.getCorrelationId())
                .occurredAt(auditEvent.getOccurredAt())
                .build();
    }
}
