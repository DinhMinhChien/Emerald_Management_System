package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.DashboardResponse;
import org.example.emeraldmanagementsystem.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<BaseResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(dashboardService.getDashboard(), "Lấy dữ liệu tổng quan thành công")
        );
    }
}
