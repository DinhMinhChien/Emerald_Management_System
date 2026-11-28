package org.example.emeraldmanagementsystem.controller;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.InvoicePayRequest;
import org.example.emeraldmanagementsystem.dto.request.InvoiceVoidRequest;
import org.example.emeraldmanagementsystem.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/invoices")
public class InvoiceController {
    private final InvoiceService invoiceService;

    @GetMapping("/{id}/print")
    public ResponseEntity<byte[]> printInvoice(
            @PathVariable String id,
            @RequestParam(defaultValue = "invoice") String type
    ) {
        InvoiceService.InvoicePrintFile file = invoiceService.printInvoice(id, type);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.filename() + "\""
                )
                .body(file.content());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) String periodId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String towerId,
            @RequestParam(required = false) String apartmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "xlsx") String format
    ) {
        InvoiceService.InvoiceExportFile file = invoiceService.exportInvoices(
                periodId,
                year,
                month,
                towerId,
                apartmentId,
                status,
                q,
                format
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\""
                )
                .body(file.content());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<InvoiceDetailResponse>> getInvoiceDetails(
            @PathVariable String id
    ) {
        InvoiceDetailResponse response = invoiceService.getInvoiceDetails(id);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy chi tiết hóa đơn thành công")
        );
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<BaseResponse<InvoiceDetailResponse>> payInvoice(
            @PathVariable String id,
            @RequestBody @Valid InvoicePayRequest request
    ) {
        InvoiceDetailResponse response = invoiceService.payInvoice(id, request);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Ghi nhận thanh toán thành công")
        );
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<BaseResponse<InvoiceDetailResponse>> voidInvoice(
            @PathVariable String id,
            @RequestBody @Valid InvoiceVoidRequest request
    ) {
        InvoiceDetailResponse response = invoiceService.voidInvoice(id, request);
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Hủy hóa đơn thành công")
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<InvoiceSummaryResponse>> getSummary(
            @RequestParam(required = false) String periodId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String towerId
    ) {
        InvoiceSummaryResponse response = invoiceService.getSummary(
                periodId,
                year,
                month,
                towerId
        );
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy tổng quan hóa đơn thành công")
        );
    }

    @GetMapping
    public ResponseEntity<BaseResponse<InvoiceListResponse>> getInvoices(
            @RequestParam(required = false) String periodId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String towerId,
            @RequestParam(required = false) String apartmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        InvoiceListResponse response = invoiceService.getInvoices(
                periodId,
                year,
                month,
                towerId,
                apartmentId,
                status,
                q,
                page,
                size
        );
        return ResponseEntity.ok(
                BaseResponse.ofSuccess(response, "Lấy danh sách hóa đơn thành công")
        );
    }
}
