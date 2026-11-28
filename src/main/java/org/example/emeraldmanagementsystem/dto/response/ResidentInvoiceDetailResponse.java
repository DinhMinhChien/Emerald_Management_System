package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ResidentInvoiceDetailResponse(
        String id,
        Period period,
        Apartment apartment,
        BigDecimal areaM2,
        String serviceTierName,
        InvoiceStatus status,
        String displayStatus,
        LocalDate dueDate,
        OffsetDateTime issuedAt,
        List<InvoiceItem> items,
        Long totalAmount,
        Payment payment
) {
    public record Period(String id, int year, int month) {
    }

    public record Apartment(String id, String code, String towerCode, String towerName) {
    }

    public record InvoiceItem(
            FeeType feeType,
            String description,
            int quantity,
            Long unitAmount,
            Long amount
    ) {
    }

    public record Payment(LocalDate paidOn, String note) {
    }
}
