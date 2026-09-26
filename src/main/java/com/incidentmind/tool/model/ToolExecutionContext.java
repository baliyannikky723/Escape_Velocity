package com.incidentmind.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    private UUID correlationId;
    private UUID agentRunId;
    private UUID investigationId;
    private UUID taskId;
}
