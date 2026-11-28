package org.example.emeraldmanagementsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.ResidencyMoveOutRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidencyMoveOutResponse;
import org.example.emeraldmanagementsystem.service.ResidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/residencies")
public class ResidencyController {
    private final ResidentService residentService;

    @PostMapping("/{residencyId}/move-out")
    public ResponseEntity<BaseResponse<ResidencyMoveOutResponse>> moveOut(
            @PathVariable String residencyId,
            @RequestBody @Valid ResidencyMoveOutRequest request
    ) {
        ResidencyMoveOutResponse response = residentService.moveOutResidency(residencyId, request);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Ghi nhận cư dân chuyển đi thành công"));
    }
}
