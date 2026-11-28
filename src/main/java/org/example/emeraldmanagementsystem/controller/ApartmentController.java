package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.dto.request.ApartmentCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.VehicleCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentResidenciesResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.service.ApartmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/apartments")
public class ApartmentController {
    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<BaseResponse<ApartmentListResponse>> getApartments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String towerId,
            @RequestParam(required = false) ApartmentStatus status,
            @RequestParam(required = false) Boolean hasHead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ApartmentListResponse response = apartmentService.getApartments(q, towerId, status, hasHead, page, size);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy danh sách căn hộ thành công"));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ApartmentDetailResponse>> createApartment(
            @RequestBody @Valid ApartmentCreateRequest request
    ) {
        ApartmentDetailResponse response = apartmentService.createApartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofCreated(response, "Tạo căn hộ thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ApartmentDetailResponse>> getApartmentDetails(
            @PathVariable String id
    ) {
        ApartmentDetailResponse response = apartmentService.getApartmentDetails(id);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy thông tin căn hộ thành công"));
    }

    @GetMapping("/{id}/residencies")
    public ResponseEntity<BaseResponse<ApartmentResidenciesResponse>> getApartmentResidencies(
            @PathVariable String id,
            @RequestParam(required = false) Boolean current
    ) {
        ApartmentResidenciesResponse response = apartmentService.getApartmentResidencies(id, current);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy danh sách cư trú thành công"));
    }

    @GetMapping("/{id}/vehicles")
    public ResponseEntity<BaseResponse<ApartmentVehiclesResponse>> getApartmentVehicles(
            @PathVariable String id,
            @RequestParam(defaultValue = "ACTIVE") String status
    ) {
        ApartmentVehiclesResponse response = apartmentService.getApartmentVehicles(id, status);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy danh sách phương tiện thành công"));
    }

    @PostMapping("/{id}/vehicles")
    public ResponseEntity<BaseResponse<ApartmentVehiclesResponse.VehicleItem>> createVehicle(
            @PathVariable String id,
            @RequestBody @Valid VehicleCreateRequest request
    ) {
        ApartmentVehiclesResponse.VehicleItem response = apartmentService.createVehicle(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofCreated(response, "Thêm phương tiện thành công"));
    }

}
