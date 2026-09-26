package com.incidentmind.recovery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "incidentmind.recovery")
public class RecoveryProperties {

    private boolean enabled = true;
    private int maxAttempts = 3;
    private long initialBackoffMs = 200;
    private long maxBackoffMs = 3000;
    private double backoffMultiplier = 2.0;
    private boolean exponentialBackoff = true;
}
