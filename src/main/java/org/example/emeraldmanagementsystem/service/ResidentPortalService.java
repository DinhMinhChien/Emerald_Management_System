package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.response.ResidentHomeResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentProfileResponse;
import org.example.emeraldmanagementsystem.dto.request.HouseholdMemberCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.HouseholdMemberCreateResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentDetailResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ResidentPortalService {
    ResidentHomeResponse getHome();

    ResidentProfileResponse getProfile();

    HouseholdMemberCreateResponse createHouseholdMember(HouseholdMemberCreateRequest request);

    ResidentInvoiceListResponse getInvoices(int page, int size);

    ResidentInvoiceDetailResponse getInvoiceDetails(String invoiceId);

    ResidentIncidentListResponse getIncidents(String status, int page, int size);

    ResidentIncidentDetailResponse getIncidentDetails(String incidentId);

    ResidentIncidentDetailResponse createIncident(String title, String area, String description, List<MultipartFile> files);

    ResidentIncidentDetailResponse cancelIncident(String incidentId);
}
