package com.incidentmind.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.tool.entity.ToolCall;
import com.incidentmind.tool.entity.ToolCallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolExecutionResponse {

    private UUID toolCallId;
    private UUID agentRunId;
    private String toolName;
    private String toolType;
    private ToolCallStatus status;
    private Integer httpStatus;
    private String errorCode;
    private String errorMessage;
    private Integer attemptNumber;
    private Long durationMs;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> sanitizedParameters;

    public static ToolExecutionResponse fromEntity(ToolCall call) {
        if (call == null) return null;

        return ToolExecutionResponse.builder()
                .toolCallId(call.getId())
                .agentRunId(call.getAgentRunId())
                .toolName(call.getToolName())
                .toolType(call.getToolType())
                .status(call.getStatus())
                .httpStatus(call.getHttpStatus())
                .errorCode(call.getErrorCode())
                .errorMessage(call.getErrorMessage())
                .attemptNumber(call.getAttemptNumber())
                .durationMs(call.getDurationMs())
                .startedAt(call.getStartedAt())
                .completedAt(call.getCompletedAt())
                .build();
    }
}
