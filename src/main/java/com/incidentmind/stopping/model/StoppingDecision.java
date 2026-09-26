package com.incidentmind.stopping.model;

public enum StoppingDecision {
    CONTINUE,
    STOP_SUCCESS,
    STOP_LIMIT_REACHED,
    STOP_TIMEOUT,
    STOP_NO_VALID_ACTION,
    STOP_BLOCKED,
    HUMAN_APPROVAL_REQUIRED
}
