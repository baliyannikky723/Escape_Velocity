package com.incidentmind.investigation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HumanActionRequest {

    @NotBlank(message = "action is required (CONTINUE, APPROVE_ACTION, REJECT_ACTION, MODIFY_PLAN, STOP)")
    private String action;

    private String notes;

    private String approvedAction;

    @Builder.Default
    private List<String> modifiedDirections = new ArrayList<>();
}
