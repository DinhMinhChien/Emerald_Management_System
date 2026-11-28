package org.example.emeraldmanagementsystem.dto.response;

public record IncidentSummaryResponse(
        long total,
        long newCount,
        long acknowledged,
        long inProgress,
        long closed,
        long cancelled
) {
}
