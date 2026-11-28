package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, String> {
    long countByDeletedFalseAndStatus(IncidentStatus status);

    List<Incident> findTop3ByDeletedFalseOrderByCreatedAtDesc();
}
