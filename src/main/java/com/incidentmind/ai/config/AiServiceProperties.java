package com.incidentmind.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai-service")
@Getter
@Setter
public class AiServiceProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8000";
    private int timeoutSeconds = 5;
    private int maxConsecutiveCriticRejections = 3;
    private int maxPlanningFailures = 2;
}
