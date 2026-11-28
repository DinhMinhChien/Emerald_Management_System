package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record InvoicePayRequest(
        @NotNull LocalDate paidOn,
        String note
) {
}
