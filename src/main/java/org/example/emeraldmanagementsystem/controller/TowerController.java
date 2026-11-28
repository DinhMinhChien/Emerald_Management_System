package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.TowerListResponse;
import org.example.emeraldmanagementsystem.service.TowerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TowerController {
    private final TowerService towerService;

    @GetMapping("/towers")
    public ResponseEntity<BaseResponse<TowerListResponse>> getTowers() {
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(towerService.getTowers(), "Lấy danh sách tòa nhà thành công")
        );
    }
}
