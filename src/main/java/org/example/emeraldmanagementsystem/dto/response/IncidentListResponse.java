package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record IncidentListResponse(
        List<IncidentItem> items,
        long total,
        int page,
        int size
) {
    public record IncidentItem(
            String id,
            String title,
            String area,
            Tower tower,
            String reporterName,
            String reporterApartmentCode,
            IncidentChannel channel,
            String assigneeName,
            IncidentStatus status,
            IncidentCancelReason cancelReason,
            OffsetDateTime createdAt
    ) {
    }

    public record Tower(String id, String code, String name) {
    }
}
