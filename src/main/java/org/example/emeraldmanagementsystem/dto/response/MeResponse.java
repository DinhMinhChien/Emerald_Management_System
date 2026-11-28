package org.example.emeraldmanagementsystem.dto.response;

public record MeResponse(
        String role,
        String fullName,
        boolean mustChangePassword,
        String apartmentCode
) {
}
