package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.request.ResidentHeadCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentPasswordResetRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidencyMoveOutRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentUpdateRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentHeadCreateResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentPasswordResetResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentAccountLockResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidencyMoveOutResponse;

public interface ResidentService {
    ResidentListResponse getResidents(
            String query,
            String towerId,
            boolean residing,
            int page,
            int size
    );

    ResidentDetailResponse getResidentDetails(String residentId);

    ResidentDetailResponse updateResident(String residentId, ResidentUpdateRequest request);

    ResidentPasswordResetResponse resetResidentPassword(
            String residentId,
            ResidentPasswordResetRequest request
    );

    ResidentAccountLockResponse lockResidentAccount(String residentId);

    ResidencyMoveOutResponse moveOutResidency(String residencyId, ResidencyMoveOutRequest request);

    ResidentHeadCreateResponse createResidentHead(ResidentHeadCreateRequest request);
}
