package org.example.emeraldmanagementsystem.dto.response;

import java.util.List;

public record ResidentHomeResponse(
        Resident resident,
        Apartment apartment,
        InvoiceSummaryItem currentInvoice,
        List<OpenIncidentItem> openIncidents,
        List<InvoiceSummaryItem> recentInvoices
) {
    public record Resident(String fullName) {
    }

    public record Apartment(String id, String code, String towerName) {
    }

    public record InvoiceSummaryItem(
            String id,
            Period period,
            Long totalAmount,
            java.time.LocalDate dueDate,
            String displayStatus
    ) {
    }

    public record Period(int year, int month) {
    }

    public record OpenIncidentItem(String id, String title, org.example.emeraldmanagementsystem.common.enums.IncidentStatus status) {
    }
}
