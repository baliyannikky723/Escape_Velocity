package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.stopping.model.StoppingDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoppingSummaryDto {

    private StoppingDecision decision;
    private String reason;
    private boolean limitReached;
    private boolean timedOut;
    private boolean humanApprovalRequired;
    private boolean completedSuccessfully;
}
