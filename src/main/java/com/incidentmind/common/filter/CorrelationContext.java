package com.incidentmind.common.filter;

import java.util.UUID;

public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void setCorrelationId(String correlationId) {
        CURRENT_CORRELATION_ID.set(correlationId);
    }

    public static String getCorrelationId() {
        String id = CURRENT_CORRELATION_ID.get();
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static UUID getCorrelationIdAsUuid() {
        String id = CURRENT_CORRELATION_ID.get();
        if (id == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(id.getBytes());
        }
    }

    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
    }
}
