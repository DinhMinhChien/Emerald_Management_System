package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;

public record ResidentInvoiceListResponse(
        Outstanding outstanding,
        List<InvoiceItem> items,
        long total,
        int page,
        int size
) {
    public record Outstanding(
            long invoiceCount,
            long totalAmount,
            LocalDate earliestDueDate,
            String invoiceId
    ) {
    }

    public record InvoiceItem(
            String id,
            Period period,
            Long totalAmount,
            LocalDate dueDate,
            String displayStatus,
            LocalDate paidOn
    ) {
    }

    public record Period(int year, int month) {
    }
}
