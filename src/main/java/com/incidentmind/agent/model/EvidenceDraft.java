package com.incidentmind.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceDraft {

    private String sourceType;
    private String sourceReference;
    private String claim;
    private Map<String, Object> rawData;
    private BigDecimal confidence;
    private UUID toolCallId;
}
