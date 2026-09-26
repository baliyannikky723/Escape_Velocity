package com.incidentmind.recovery.policy;

public interface BackoffStrategy {

    long calculateBackoffMs(int attemptNumber);

    void applyBackoff(long backoffMs) throws InterruptedException;
}
