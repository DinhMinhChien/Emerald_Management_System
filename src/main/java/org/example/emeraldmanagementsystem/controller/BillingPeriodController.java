package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.BillingAvailableMonthsResponse;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodPreviewResponse;
import org.example.emeraldmanagementsystem.dto.request.BillingPeriodIssueRequest;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodIssueResponse;
import org.example.emeraldmanagementsystem.service.BillingPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/billing-periods")
public class BillingPeriodController {
    private final BillingPeriodService billingPeriodService;

    @GetMapping("/available-months")
    public ResponseEntity<BaseResponse<BillingAvailableMonthsResponse>> getAvailableMonths() {
        BillingAvailableMonthsResponse response = billingPeriodService.getAvailableMonths();
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy danh sách tháng chưa có kỳ thành công"));
    }

    @GetMapping("/preview")
    public ResponseEntity<BaseResponse<BillingPeriodPreviewResponse>> preview(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String towerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        BillingPeriodPreviewResponse response = billingPeriodService.preview(
                year,
                month,
                q,
                towerId,
                page,
                size
        );
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Xem trước kỳ hóa đơn thành công"));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<BillingPeriodIssueResponse>> issue(
            @RequestBody @Valid BillingPeriodIssueRequest request
    ) {
        BillingPeriodIssueResponse response = billingPeriodService.issue(request);
        return ResponseEntity.status(201).body(
                BaseResponse.ofCreated(response, "Phát hành kỳ hóa đơn thành công")
        );
    }
}
