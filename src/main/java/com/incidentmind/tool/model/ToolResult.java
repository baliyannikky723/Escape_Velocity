package com.incidentmind.tool.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.incidentmind.tool.entity.ToolCallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {

    private boolean success;
    private ToolCallStatus status;
    private String toolName;
    private String toolType;
    private Integer httpStatus;
    private String errorCode;
    private String errorMessage;
    private ErrorClassification errorClassification;
    private Map<String, Object> requestPayload;
    private Map<String, Object> responsePayload;
    private Map<String, String> rateLimitInfo;
    private Long durationMs;
    private UUID toolCallId;
    private UUID correlationId;

    public static ToolResult success(String toolName,
                                     String toolType,
                                     Integer httpStatus,
                                     Map<String, Object> responsePayload,
                                     Map<String, String> rateLimitInfo,
                                     Long durationMs) {
        return ToolResult.builder()
                .success(true)
                .status(ToolCallStatus.SUCCESS)
                .toolName(toolName)
                .toolType(toolType)
                .httpStatus(httpStatus)
                .responsePayload(responsePayload)
                .rateLimitInfo(rateLimitInfo)
                .durationMs(durationMs)
                .build();
    }

    public static ToolResult failure(String toolName,
                                     String toolType,
                                     ToolCallStatus status,
                                     Integer httpStatus,
                                     String errorCode,
                                     String errorMessage,
                                     ErrorClassification errorClassification,
                                     Map<String, Object> responsePayload,
                                     Map<String, String> rateLimitInfo,
                                     Long durationMs) {
        return ToolResult.builder()
                .success(false)
                .status(status != null ? status : ToolCallStatus.FAILED)
                .toolName(toolName)
                .toolType(toolType)
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .errorClassification(errorClassification)
                .responsePayload(responsePayload)
                .rateLimitInfo(rateLimitInfo)
                .durationMs(durationMs)
                .build();
    }
}
