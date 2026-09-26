package com.incidentmind.incident.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentmind.common.exception.GlobalExceptionHandler;
import com.incidentmind.common.exception.ResourceNotFoundException;
import com.incidentmind.common.filter.CorrelationIdFilter;
import com.incidentmind.incident.dto.CreateIncidentRequest;
import com.incidentmind.incident.dto.IncidentResponse;
import com.incidentmind.incident.entity.IncidentSeverity;
import com.incidentmind.incident.entity.IncidentStatus;
import com.incidentmind.incident.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IncidentService incidentService;

    @InjectMocks
    private IncidentController incidentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(incidentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    @DisplayName("1. Create incident successfully - returns 201 Created and response payload")
    void createIncident_Success() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("Checkout API degradation")
                .description("Checkout error rate increased from 2% to 18%")
                .severity(IncidentSeverity.P1)
                .serviceName("checkout-service")
                .environment("production")
                .build();

        IncidentResponse response = IncidentResponse.builder()
                .id(id)
                .incidentKey("INC-000001")
                .title("Checkout API degradation")
                .description("Checkout error rate increased from 2% to 18%")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(incidentService.createIncident(any(CreateIncidentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/incidents")
                        .header("X-Correlation-Id", "test-corr-id-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "test-corr-id-123"))
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.incidentKey", is("INC-000001")))
                .andExpect(jsonPath("$.title", is("Checkout API degradation")))
                .andExpect(jsonPath("$.severity", is("P1")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.serviceName", is("checkout-service")))
                .andExpect(jsonPath("$.environment", is("production")));

        verify(incidentService).createIncident(any(CreateIncidentRequest.class));
    }

    @Test
    @DisplayName("2. Reject incident with missing title - returns 400 Bad Request")
    void createIncident_MissingTitle_Returns400() throws Exception {
        String invalidJson = """
                {
                    "title": "",
                    "description": "Checkout error rate increased",
                    "severity": "P1",
                    "serviceName": "checkout-service",
                    "environment": "production"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.title", notNullValue()))
                .andExpect(header().exists("X-Correlation-Id"));

        verify(incidentService, never()).createIncident(any());
    }

    @Test
    @DisplayName("3. Reject invalid severity - returns 400 Bad Request")
    void createIncident_InvalidSeverity_Returns400() throws Exception {
        String invalidJson = """
                {
                    "title": "Database connection spike",
                    "description": "DB connection pool exhausted",
                    "severity": "CRITICAL_INVALID",
                    "serviceName": "db-proxy",
                    "environment": "production"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("MALFORMED_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("CRITICAL_INVALID")));

        verify(incidentService, never()).createIncident(any());
    }

    @Test
    @DisplayName("Get incident by ID successfully - returns 200 OK")
    void getIncident_Success() throws Exception {
        UUID id = UUID.randomUUID();
        IncidentResponse response = IncidentResponse.builder()
                .id(id)
                .incidentKey("INC-000001")
                .title("Checkout API degradation")
                .description("Checkout error rate increased from 2% to 18%")
                .severity(IncidentSeverity.P1)
                .status(IncidentStatus.OPEN)
                .serviceName("checkout-service")
                .environment("production")
                .build();

        when(incidentService.getIncidentById(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.incidentKey", is("INC-000001")));
    }

    @Test
    @DisplayName("Get nonexistent incident - returns 404 Not Found")
    void getIncident_NotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(incidentService.getIncidentById(id))
                .thenThrow(new ResourceNotFoundException("Incident", id));

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString(id.toString())));
    }
}
