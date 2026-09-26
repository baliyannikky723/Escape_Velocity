package com.incidentmind.tool.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PayloadSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token",
            "accesstoken",
            "access_token",
            "authorization",
            "bearer",
            "password",
            "secret",
            "apikey",
            "api_key",
            "privatekey",
            "private_key"
    );

    private static final String REDACTED_VALUE = "[REDACTED]";

    private PayloadSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }

        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                sanitized.put(key, REDACTED_VALUE);
            } else if (value instanceof Map<?, ?> mapValue) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castedMap = (Map<String, Object>) mapValue;
                sanitized.put(key, sanitize(castedMap));
            } else if (value instanceof List<?> listValue) {
                sanitized.put(key, sanitizeList(listValue));
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private static List<?> sanitizeList(List<?> list) {
        return list.stream().map(item -> {
            if (item instanceof Map<?, ?> mapItem) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castedMap = (Map<String, Object>) mapItem;
                return sanitize(castedMap);
            }
            return item;
        }).toList();
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase().replaceAll("[-_]", "");
        for (String sensitive : SENSITIVE_KEYS) {
            if (normalized.contains(sensitive.replaceAll("[-_]", ""))) {
                return true;
            }
        }
        return false;
    }
}
