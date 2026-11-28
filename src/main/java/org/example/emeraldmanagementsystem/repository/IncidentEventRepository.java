package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.IncidentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, String> {
    List<IncidentEvent> findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(String incidentId);
}
