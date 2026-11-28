package org.example.emeraldmanagementsystem.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BillingPeriodIssueResponse(
        String periodId,
        int year,
        int month,
        LocalDate dueDate,
        long issuedInvoiceCount,
        long skippedVacantCount,
        long totalAmount,
        OffsetDateTime issuedAt,
        String issuedBy
) {
}
