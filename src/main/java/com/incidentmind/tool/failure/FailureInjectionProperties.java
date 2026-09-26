package com.incidentmind.tool.failure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "incidentmind.tools.failure-injection")
public class FailureInjectionProperties {

    private boolean enabled = false;
    private Map<String, String> rules = new HashMap<>();

    public InjectedFailureMode getFailureModeForTool(String toolName) {
        if (!enabled || rules == null || toolName == null) {
            return InjectedFailureMode.NONE;
        }

        String modeStr = rules.get(toolName);
        if (modeStr == null) {
            modeStr = rules.get("default");
        }
        if (modeStr == null) {
            return InjectedFailureMode.NONE;
        }

        try {
            return InjectedFailureMode.valueOf(modeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return InjectedFailureMode.NONE;
        }
    }
}
