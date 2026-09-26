package com.incidentmind.audit.repository;

import com.incidentmind.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByInvestigationIdOrderByOccurredAtAsc(UUID investigationId);

    List<AuditEvent> findByCorrelationIdOrderByOccurredAtAsc(UUID correlationId);
}
