package com.incidentmind.tool.model;

public enum ErrorClassification {
    HTTP_4XX,
    HTTP_5XX,
    TIMEOUT,
    NETWORK_ERROR,
    MALFORMED_RESPONSE,
    VALIDATION_ERROR,
    UNKNOWN;

    public static ErrorClassification fromHttpStatus(Integer httpStatus) {
        if (httpStatus == null) {
            return UNKNOWN;
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return HTTP_4XX;
        }
        if (httpStatus >= 500 && httpStatus < 600) {
            return HTTP_5XX;
        }
        return UNKNOWN;
    }
}
