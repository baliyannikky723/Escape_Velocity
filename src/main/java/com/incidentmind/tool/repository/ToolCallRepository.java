package com.incidentmind.tool.repository;

import com.incidentmind.tool.entity.ToolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ToolCallRepository extends JpaRepository<ToolCall, UUID> {

    List<ToolCall> findByAgentRunIdOrderByCreatedAtAsc(UUID agentRunId);

    List<ToolCall> findByAgentRunIdInOrderByCreatedAtAsc(List<UUID> agentRunIds);
}
