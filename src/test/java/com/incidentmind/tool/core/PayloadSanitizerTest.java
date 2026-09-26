package com.incidentmind.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadSanitizerTest {

    @Test
    @DisplayName("PayloadSanitizer: redacts access tokens, passwords, and sensitive keys")
    void sanitize_RedactsSensitiveKeys() {
        Map<String, Object> input = Map.of(
                "owner", "octocat",
                "token", "ghp_1234567890abcdef",
                "Authorization", "Bearer secret-token-xyz",
                "nested", Map.of(
                        "apiKey", "super-secret-key",
                        "safeField", "public-value"
                ),
                "listField", List.of(
                        Map.of("password", "p@ssword", "user", "admin")
                )
        );

        Map<String, Object> sanitized = PayloadSanitizer.sanitize(input);

        assertThat(sanitized.get("owner")).isEqualTo("octocat");
        assertThat(sanitized.get("token")).isEqualTo("[REDACTED]");
        assertThat(sanitized.get("Authorization")).isEqualTo("[REDACTED]");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) sanitized.get("nested");
        assertThat(nested.get("apiKey")).isEqualTo("[REDACTED]");
        assertThat(nested.get("safeField")).isEqualTo("public-value");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) sanitized.get("listField");
        assertThat(list.get(0).get("password")).isEqualTo("[REDACTED]");
        assertThat(list.get(0).get("user")).isEqualTo("admin");
    }
}
