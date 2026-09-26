package com.incidentmind.task.repository;

import com.incidentmind.task.entity.InvestigationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvestigationTaskRepository extends JpaRepository<InvestigationTask, UUID> {

    List<InvestigationTask> findByInvestigationIdOrderByCreatedAtAsc(UUID investigationId);
}
