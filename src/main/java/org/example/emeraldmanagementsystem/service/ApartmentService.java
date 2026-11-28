package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.dto.request.ApartmentCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.VehicleCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.VehicleRevokeRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentResidenciesResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;

public interface ApartmentService {
    ApartmentListResponse getApartments(
            String query,
            String towerId,
            ApartmentStatus status,
            Boolean hasHead,
            int page,
            int size
    );

    ApartmentDetailResponse createApartment(ApartmentCreateRequest request);

    ApartmentDetailResponse getApartmentDetails(String apartmentId);

    ApartmentResidenciesResponse getApartmentResidencies(String apartmentId, Boolean current);

    ApartmentVehiclesResponse getApartmentVehicles(String apartmentId, String status);

    ApartmentVehiclesResponse.VehicleItem createVehicle(String apartmentId, VehicleCreateRequest request);

    ApartmentVehiclesResponse.VehicleItem revokeVehicle(String vehicleId, VehicleRevokeRequest request);
}
