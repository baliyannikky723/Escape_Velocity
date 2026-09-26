package com.incidentmind.agent.repository;

import com.incidentmind.agent.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
