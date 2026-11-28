package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.ResidentHeadCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentPasswordResetRequest;
import org.example.emeraldmanagementsystem.dto.request.ResidentUpdateRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentAccountLockResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentHeadCreateResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentPasswordResetResponse;
import org.example.emeraldmanagementsystem.service.ResidentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/residents")
public class ResidentController {
    private final ResidentService residentService;

    @GetMapping
    public ResponseEntity<BaseResponse<ResidentListResponse>> getResidents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String towerId,
            @RequestParam(defaultValue = "true") boolean residing,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ResidentListResponse response = residentService.getResidents(q, towerId, residing, page, size);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy danh sách cư dân thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ResidentDetailResponse>> getResidentDetails(
            @PathVariable String id
    ) {
        ResidentDetailResponse response = residentService.getResidentDetails(id);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy thông tin cư dân thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ResidentDetailResponse>> updateResident(
            @PathVariable String id,
            @RequestBody @Valid ResidentUpdateRequest request
    ) {
        ResidentDetailResponse response = residentService.updateResident(id, request);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Cập nhật thông tin cư dân thành công"));
    }

    @PostMapping("/{id}/account/reset-password")
    public ResponseEntity<BaseResponse<ResidentPasswordResetResponse>> resetResidentPassword(
            @PathVariable String id,
            @RequestBody(required = false) ResidentPasswordResetRequest request
    ) {
        ResidentPasswordResetResponse response = residentService.resetResidentPassword(id, request);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Đặt lại mật khẩu thành công"));
    }

    @PostMapping("/{id}/account/lock")
    public ResponseEntity<BaseResponse<ResidentAccountLockResponse>> lockResidentAccount(
            @PathVariable String id
    ) {
        ResidentAccountLockResponse response = residentService.lockResidentAccount(id);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Khóa tài khoản cư dân thành công"));
    }

    @PostMapping("/heads")
    public ResponseEntity<BaseResponse<ResidentHeadCreateResponse>> createResidentHead(
            @RequestBody @Valid ResidentHeadCreateRequest request
    ) {
        ResidentHeadCreateResponse response = residentService.createResidentHead(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofCreated(response, "Tạo chủ hộ thành công"));
    }
}
