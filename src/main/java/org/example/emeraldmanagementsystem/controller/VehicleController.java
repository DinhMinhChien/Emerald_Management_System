package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.VehicleRevokeRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.service.ApartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/vehicles")
public class VehicleController {
    private final ApartmentService apartmentService;

    @PostMapping("/{id}/revoke")
    public ResponseEntity<BaseResponse<ApartmentVehiclesResponse.VehicleItem>> revokeVehicle(
            @PathVariable String id,
            @RequestBody(required = false) VehicleRevokeRequest request
    ) {
        ApartmentVehiclesResponse.VehicleItem response = apartmentService.revokeVehicle(id, request);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Thu hồi thẻ xe thành công"));
    }
}
