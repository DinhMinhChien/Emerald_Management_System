package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.CurrentFeeTariffsResponse;
import org.example.emeraldmanagementsystem.service.FeeTariffService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/fee-tariffs")
public class FeeTariffController {
    private final FeeTariffService feeTariffService;

    @GetMapping("/current")
    public ResponseEntity<BaseResponse<CurrentFeeTariffsResponse>> getCurrent() {
        CurrentFeeTariffsResponse response = feeTariffService.getCurrent();
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy biểu giá hiện tại thành công")
        );
    }
}
