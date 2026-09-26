package com.incidentmind.investigation.repository;

import com.incidentmind.investigation.entity.Investigation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {

    List<Investigation> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
