package org.example.emeraldmanagementsystem.dto.response;

public record InvoiceSummaryResponse(
        long total,
        long paid,
        long pending,
        long overdue,
        long voidCount
) {
}
