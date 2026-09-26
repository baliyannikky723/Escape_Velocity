package com.incidentmind.tool.github;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String apiBaseUrl = "https://api.github.com";
    private String token;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
}
