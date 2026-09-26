package com.incidentmind.incident.service;

import com.incidentmind.audit.entity.ActorType;
import com.incidentmind.audit.entity.AuditEventType;
import com.incidentmind.audit.service.AuditService;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.incident.dto.CreateIncidentRequest;
import com.incidentmind.incident.dto.IncidentResponse;
import com.incidentmind.incident.entity.Incident;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.repository.IncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private IncidentServiceImpl incidentService;

    @Test
    @DisplayName("Create incident formats key INC-000001, sets status OPEN, and publishes audit event")
    void createIncident_Success() {
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("Payment gateway timeout")
                .description("504 Gateway Timeout during checkout charge calls")
                .severity(IncidentSeverity.P1)
                .serviceName("payment-gateway")
                .environment("production")
                .build();

        when(incidentRepository.getNextIncidentSequenceValue()).thenReturn(1L);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponse response = incidentService.createIncident(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getIncidentKey()).isEqualTo("INC-000001");
        assertThat(response.getTitle()).isEqualTo("Payment gateway timeout");
        assertThat(response.getSeverity()).isEqualTo(IncidentSeverity.P1);
        assertThat(response.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(response.getServiceName()).isEqualTo("payment-gateway");
        assertThat(response.getEnvironment()).isEqualTo("production");

        verify(auditService).recordEvent(
                isNull(),
                isNull(),
                isNull(),
                eq(AuditEventType.INCIDENT_CREATED),
                eq(ActorType.USER),
                eq("api-user"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Get incident by ID returns incident when found")
    void getIncidentById_Found() {
        UUID id = UUID.randomUUID();
        Incident incident = Incident.builder()
                .id(id)
                .incidentKey("INC-000042")
                .title("Database connection spike")
                .description("Postgres active connections reached max pool")
                .severity(IncidentSeverity.P2)
                .status(IncidentStatus.INVESTIGATING)
                .serviceName("orders-db")
                .environment("staging")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

        IncidentResponse response = incidentService.getIncidentById(id);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getIncidentKey()).isEqualTo("INC-000042");
        assertThat(response.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
    }

    @Test
    @DisplayName("Get incident by ID throws ResourceNotFoundException when not found")
    void getIncidentById_NotFound() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncidentById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
