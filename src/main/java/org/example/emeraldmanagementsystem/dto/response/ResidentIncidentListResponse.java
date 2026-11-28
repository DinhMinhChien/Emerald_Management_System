package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ResidentIncidentListResponse(
        List<IncidentItem> items,
        long total,
        int page,
        int size
) {
    public record IncidentItem(
            String id,
            String title,
            IncidentStatus status,
            String assigneeName,
            IncidentCancelReason cancelReason,
            OffsetDateTime createdAt
    ) {
    }
}
