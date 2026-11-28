package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.IncidentCancelReason;
import org.example.emeraldmanagementsystem.common.enums.IncidentChannel;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;

import java.time.OffsetDateTime;
import java.util.List;

public record IncidentDetailResponse(
        String id,
        String title,
        IncidentStatus status,
        String area,
        Tower tower,
        Apartment apartment,
        Reporter reporter,
        String reporterName,
        String reporterApartmentCode,
        IncidentChannel channel,
        String assigneeName,
        String description,
        List<Attachment> attachments,
        List<TimelineItem> timeline,
        String resultNote,
        Cancellation cancellation,
        List<String> allowedActions,
        OffsetDateTime createdAt
) {
    public record Tower(String id, String code, String name) {
    }

    public record Apartment(String id, String code) {
    }

    public record Reporter(String residentId, String fullName, ResidencyRelation relation) {
    }

    public record Attachment(String id, String fileName, String url) {
    }

    public record TimelineItem(
            IncidentStatus status,
            OffsetDateTime at,
            String actorName,
            String assigneeName,
            String note
    ) {
    }

    public record Cancellation(
            IncidentCancelReason reason,
            String note,
            String cancelledBy,
            String cancelledByName,
            OffsetDateTime cancelledAt
    ) {
    }
}
