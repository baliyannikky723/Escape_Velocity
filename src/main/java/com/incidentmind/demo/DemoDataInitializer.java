package com.incidentmind.demo;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "incidentmind.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;

    public DemoDataInitializer(IncidentRepository incidentRepository, AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String demoIncidentKey = "INC-000001";
        if (incidentRepository.findByIncidentKey(demoIncidentKey).isPresent()) {
            log.info("Demo incident {} already exists. Skipping initialization.", demoIncidentKey);
            return;
        }

        Instant now = Instant.now();
        Incident incident = Incident.builder()
                .id(UUID.randomUUID())
                .incidentKey(demoIncidentKey)
                .title("Checkout API degradation")
                .description("Checkout error rate increased from 2% to 18% after a recent deployment.")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Incident saved = incidentRepository.save(incident);
        log.info("Initialized demo incident: id={}, key={}", saved.getId(), saved.getIncidentKey());

        Map<String, Object> auditData = new HashMap<>();
        auditData.put("incidentId", saved.getId().toString());
        auditData.put("incidentKey", saved.getIncidentKey());
        auditData.put("title", saved.getTitle());
        auditData.put("severity", saved.getSeverity().name());
        auditData.put("serviceName", saved.getServiceName());
        auditData.put("environment", saved.getEnvironment());
        auditData.put("source", "demo_data_initializer");

        auditService.recordEvent(
                null,
                null,
                null,
                AuditEventType.INCIDENT_CREATED,
                ActorType.SYSTEM,
                "demo-seeder",
                auditData,
                UUID.randomUUID()
        );
    }
}
