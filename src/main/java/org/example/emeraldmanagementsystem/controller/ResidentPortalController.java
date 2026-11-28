package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentHomeResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentProfileResponse;
import org.example.emeraldmanagementsystem.dto.request.HouseholdMemberCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.HouseholdMemberCreateResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentInvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ResidentIncidentDetailResponse;
import org.example.emeraldmanagementsystem.service.ResidentPortalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resident")
public class ResidentPortalController {
    private final ResidentPortalService residentPortalService;

    @GetMapping("/home")
    public ResponseEntity<BaseResponse<ResidentHomeResponse>> getHome() {
        ResidentHomeResponse response = residentPortalService.getHome();
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy trang chủ cư dân thành công")
        );
    }

    @GetMapping("/profile")
    public ResponseEntity<BaseResponse<ResidentProfileResponse>> getProfile() {
        ResidentProfileResponse response = residentPortalService.getProfile();
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy hồ sơ cư dân thành công")
        );
    }

    @GetMapping("/incidents")
    public ResponseEntity<BaseResponse<ResidentIncidentListResponse>> getIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ResidentIncidentListResponse response =
                residentPortalService.getIncidents(status, page, size);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy danh sách sự cố cư dân thành công")
        );
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<BaseResponse<ResidentIncidentDetailResponse>> getIncidentDetails(
            @PathVariable String id
    ) {
        ResidentIncidentDetailResponse response = residentPortalService.getIncidentDetails(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy chi tiết sự cố cư dân thành công")
        );
    }

    @PostMapping(value = "/incidents", consumes = "multipart/form-data")
    public ResponseEntity<BaseResponse<ResidentIncidentDetailResponse>> createIncident(
            @RequestParam("title") String title,
            @RequestParam("area") String area,
            @RequestParam("description") String description,
            @RequestParam(required = false) List<MultipartFile> files
    ) {
        ResidentIncidentDetailResponse response =
                residentPortalService.createIncident(title, area, description, files);
        return ResponseEntity.status(201).body(
                BaseResponse.ofCreated(response, "Gửi yêu cầu sự cố thành công")
        );
    }

    @PostMapping("/incidents/{id}/cancel")
    public ResponseEntity<BaseResponse<ResidentIncidentDetailResponse>> cancelIncident(
            @PathVariable String id
    ) {
        ResidentIncidentDetailResponse response = residentPortalService.cancelIncident(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Hủy yêu cầu sự cố thành công")
        );
    }

    @GetMapping("/invoices")
    public ResponseEntity<BaseResponse<ResidentInvoiceListResponse>> getInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ResidentInvoiceListResponse response = residentPortalService.getInvoices(page, size);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy danh sách hóa đơn cư dân thành công")
        );
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<BaseResponse<ResidentInvoiceDetailResponse>> getInvoiceDetails(
            @PathVariable String id
    ) {
        ResidentInvoiceDetailResponse response = residentPortalService.getInvoiceDetails(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy chi tiết hóa đơn cư dân thành công")
        );
    }

    @PostMapping("/household-members")
    public ResponseEntity<BaseResponse<HouseholdMemberCreateResponse>> createHouseholdMember(
            @RequestBody @Valid HouseholdMemberCreateRequest request
    ) {
        HouseholdMemberCreateResponse response = residentPortalService.createHouseholdMember(request);
        return ResponseEntity.status(201).body(
                BaseResponse.ofCreated(response, "Thêm thành viên thành công")
        );
    }
}
