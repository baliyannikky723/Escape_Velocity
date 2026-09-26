package com.incidentmind.incident.repository;

import com.incidentmind.incident.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByIncidentKey(String incidentKey);

    @Query(value = "SELECT nextval('incident_key_seq')", nativeQuery = true)
    Long getNextIncidentSequenceValue();
}
