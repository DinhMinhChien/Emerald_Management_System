package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;

public record InvoiceListResponse(
        List<InvoiceItem> items,
        long total,
        int page,
        int size
) {
    public record InvoiceItem(
            String id,
            Period period,
            Apartment apartment,
            String recipientName,
            Long totalAmount,
            LocalDate dueDate,
            InvoiceStatus status,
            String displayStatus,
            LocalDate paidOn
    ) {
    }

    public record Period(String id, int year, int month) {
    }

    public record Apartment(String id, String code, String towerCode) {
    }
}
