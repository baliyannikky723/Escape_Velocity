package com.incidentmind.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetadata {

    private String name;
    private String description;
    private Set<String> capabilities;
}
