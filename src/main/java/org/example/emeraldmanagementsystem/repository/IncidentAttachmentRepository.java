package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.IncidentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, String> {
    List<IncidentAttachment> findByIncidentIdAndDeletedFalseOrderByCreatedAtAscIdAsc(String incidentId);
}
