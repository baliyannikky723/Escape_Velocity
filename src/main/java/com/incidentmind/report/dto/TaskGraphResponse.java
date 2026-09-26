package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskGraphResponse {

    private UUID investigationId;
    @Builder.Default
    private List<TaskGraphNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<TaskGraphEdge> edges = new ArrayList<>();
    private int totalNodes;
    private int totalEdges;
}
