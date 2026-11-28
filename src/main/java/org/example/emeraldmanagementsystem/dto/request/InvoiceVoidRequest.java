package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InvoiceVoidRequest(
        @NotBlank String reason,
        String note
) {
}
