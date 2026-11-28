package org.example.emeraldmanagementsystem.dto.response;

import java.util.List;

public record BillingAvailableMonthsResponse(
        DefaultMonth defaultMonth,
        List<AvailableMonth> items
) {
    public record DefaultMonth(int year, int month) {
    }

    public record AvailableMonth(int year, int month, String label) {
    }
}
