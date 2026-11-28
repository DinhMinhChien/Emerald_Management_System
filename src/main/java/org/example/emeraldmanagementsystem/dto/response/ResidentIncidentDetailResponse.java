package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ResidentIncidentDetailResponse(
        String id,
        String title,
        IncidentStatus status,
        String area,
        String assigneeName,
        String description,
        List<Attachment> attachments,
        List<TimelineItem> timeline,
        String resultNote,
        Cancellation cancellation,
        boolean canCancel,
        OffsetDateTime createdAt
) {
    public record Attachment(String id, String fileName, String url) {
    }

    public record TimelineItem(
            IncidentStatus status,
            OffsetDateTime at,
            String assigneeName
    ) {
    }

    public record Cancellation(
            IncidentCancelReason reason,
            String cancelledBy
    ) {
    }
}
