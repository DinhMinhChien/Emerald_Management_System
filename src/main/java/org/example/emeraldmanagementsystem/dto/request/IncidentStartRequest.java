package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;

public record IncidentStartRequest(
        @NotBlank(message = "assigneeName không được để trống") String assigneeName
) {
}
