package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BillingPeriodIssueRequest(
        @NotNull @Min(1) @Max(9999) Integer year,
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull LocalDate dueDate
) {
}
