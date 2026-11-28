package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IncidentCancelRequest(
        @NotBlank(message = "cancelReason không được để trống") String cancelReason,
        String note
) {
}
