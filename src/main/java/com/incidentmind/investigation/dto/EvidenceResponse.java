package com.incidentmind.investigation.dto;

import com.incidentmind.evidence.entity.Evidence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceResponse {

    private UUID id;
    private UUID investigationId;
    private UUID taskId;
    private UUID agentRunId;
    private UUID toolCallId;
    private String sourceType;
    private String sourceReference;
    private String claim;
    private Map<String, Object> rawData;
    private BigDecimal confidence;
    private Instant collectedAt;
    private Instant createdAt;

    public static EvidenceResponse fromEntity(Evidence evidence) {
        if (evidence == null) {
            return null;
        }
        return EvidenceResponse.builder()
                .id(evidence.getId())
                .investigationId(evidence.getInvestigationId())
                .taskId(evidence.getTaskId())
                .agentRunId(evidence.getAgentRunId())
                .toolCallId(evidence.getToolCallId())
                .sourceType(evidence.getSourceType())
                .sourceReference(evidence.getSourceReference())
                .claim(evidence.getClaim())
                .rawData(evidence.getRawData())
                .confidence(evidence.getConfidence())
                .collectedAt(evidence.getCollectedAt())
                .createdAt(evidence.getCreatedAt())
                .build();
    }
}
