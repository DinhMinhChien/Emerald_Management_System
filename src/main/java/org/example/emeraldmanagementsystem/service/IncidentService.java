package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.response.IncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.IncidentStartRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCompleteRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCancelRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IncidentService {
    IncidentListResponse getIncidents(
            String query,
            String towerId,
            String status,
            String apartmentId,
            int page,
            int size
    );

    IncidentSummaryResponse getSummary(String towerId);

    IncidentDetailResponse getIncidentDetails(String incidentId);

    IncidentDetailResponse createIncident(
            String apartmentId,
            String reportedBy,
            String reporterName,
            String channel,
            String area,
            String title,
            String description,
            List<MultipartFile> files
    );

    IncidentDetailResponse acknowledgeIncident(String incidentId);

    IncidentDetailResponse startIncident(String incidentId, IncidentStartRequest request);

    IncidentDetailResponse completeIncident(String incidentId, IncidentCompleteRequest request);

    IncidentDetailResponse revertIncident(String incidentId);

    IncidentDetailResponse cancelIncident(String incidentId, IncidentCancelRequest request);
}
