package org.example.emeraldmanagementsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        Period period,
        Occupancy occupancy,
        Collection collection,
        long overdueApartments,
        IncidentSummary incidents,
        Todo todo,
        List<LatestIncident> latestIncidents,
        List<RecentInvoice> recentInvoices
) {
    public record Period(String id, int year, int month) {
    }

    public record Occupancy(long occupied, long total, double rate) {
    }

    public record Collection(long collectedAmount, long totalAmount, double rate) {
    }

    public record IncidentSummary(
            long needHandling,
            @JsonProperty("new") long newCount,
            long acknowledged,
            long inProgress
    ) {
    }

    public record Todo(long pendingInvoices, long overdueInvoices, long unacknowledgedIncidents) {
    }

    public record LatestIncident(
            String id,
            String title,
            String area,
            String apartmentCode,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record RecentInvoice(
            String id,
            String apartmentCode,
            String recipientName,
            Long totalAmount,
            String displayStatus,
            LocalDate dueDate
    ) {
    }
}
