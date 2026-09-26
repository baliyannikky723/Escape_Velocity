package com.incidentmind.tool.failure;

public enum InjectedFailureMode {
    NONE,
    HTTP_500,
    HTTP_503,
    HTTP_403,
    HTTP_429,
    TIMEOUT,
    MALFORMED_RESPONSE
}
