package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentListResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.IncidentDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.IncidentStartRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCompleteRequest;
import org.example.emeraldmanagementsystem.dto.request.IncidentCancelRequest;
import org.example.emeraldmanagementsystem.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/incidents")
public class IncidentController {
    private final IncidentService incidentService;

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> acknowledgeIncident(
            @PathVariable String id
    ) {
        IncidentDetailResponse response = incidentService.acknowledgeIncident(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Tiếp nhận yêu cầu thành công")
        );
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> startIncident(
            @PathVariable String id,
            @RequestBody @Valid IncidentStartRequest request
    ) {
        IncidentDetailResponse response = incidentService.startIncident(id, request);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Bắt đầu xử lý sự cố thành công")
        );
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> completeIncident(
            @PathVariable String id,
            @RequestBody @Valid IncidentCompleteRequest request
    ) {
        IncidentDetailResponse response = incidentService.completeIncident(id, request);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Hoàn thành xử lý sự cố thành công")
        );
    }

    @PostMapping("/{id}/revert")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> revertIncident(
            @PathVariable String id
    ) {
        IncidentDetailResponse response = incidentService.revertIncident(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Chuyển sự cố về trạng thái đã tiếp nhận thành công")
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> cancelIncident(
            @PathVariable String id,
            @RequestBody @Valid IncidentCancelRequest request
    ) {
        IncidentDetailResponse response = incidentService.cancelIncident(id, request);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Hủy yêu cầu sự cố thành công")
        );
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> createIncident(
            @RequestParam("apartmentId") String apartmentId,
            @RequestParam(required = false) String reportedBy,
            @RequestParam(required = false) String reporterName,
            @RequestParam("channel") String channel,
            @RequestParam("area") String area,
            @RequestParam("title") String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<MultipartFile> files
    ) {
        IncidentDetailResponse response = incidentService.createIncident(
                apartmentId,
                reportedBy,
                reporterName,
                channel,
                area,
                title,
                description,
                files
        );
        return ResponseEntity.status(201).body(
                BaseResponse.ofCreated(response, "Tạo yêu cầu sự cố thành công")
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<IncidentDetailResponse>> getIncidentDetails(
            @PathVariable String id
    ) {
        IncidentDetailResponse response = incidentService.getIncidentDetails(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy chi tiết sự cố thành công")
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<IncidentSummaryResponse>> getSummary(
            @RequestParam(required = false) String towerId
    ) {
        IncidentSummaryResponse response = incidentService.getSummary(towerId);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy tổng quan sự cố thành công")
        );
    }

    @GetMapping
    public ResponseEntity<BaseResponse<IncidentListResponse>> getIncidents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String towerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apartmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        IncidentListResponse response = incidentService.getIncidents(
                q,
                towerId,
                status,
                apartmentId,
                page,
                size
        );
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy danh sách sự cố thành công")
        );
    }
}
