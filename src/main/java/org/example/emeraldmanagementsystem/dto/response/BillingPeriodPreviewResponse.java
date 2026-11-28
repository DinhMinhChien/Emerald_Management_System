package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;

import java.math.BigDecimal;
import java.util.List;

public record BillingPeriodPreviewResponse(
        int year,
        int month,
        long invoiceCount,
        long skippedVacantCount,
        long totalAmount,
        List<Warning> warnings,
        ApartmentPage apartments
) {
    public record Warning(String apartmentCode, String code, String message) {
    }

    public record ApartmentPage(List<ApartmentItem> items, long total, int page, int size) {
    }

    public record ApartmentItem(
            String apartmentId,
            String code,
            String towerCode,
            BigDecimal areaM2,
            String serviceTierName,
            ApartmentStatus apartmentStatus,
            String recipientName,
            List<InvoiceLineItem> items,
            long totalAmount
    ) {
    }

    public record InvoiceLineItem(
            FeeType feeType,
            String description,
            int quantity,
            long unitAmount,
            long amount
    ) {
    }
}
