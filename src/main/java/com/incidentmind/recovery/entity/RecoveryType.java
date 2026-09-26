package com.incidentmind.recovery.entity;

public enum RecoveryType {
    RETRY,
    BACKOFF,
    FALLBACK_TOOL,
    REPLAN,
    SKIP,
    ABORT
}
