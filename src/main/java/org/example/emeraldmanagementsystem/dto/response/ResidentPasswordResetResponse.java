package org.example.emeraldmanagementsystem.dto.response;

public record ResidentPasswordResetResponse(
        String userId,
        String temporaryPassword,
        boolean mustChangePassword
) {
}
