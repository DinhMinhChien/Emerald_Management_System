package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;

public record ResidentHeadCreateResponse(
        String residentId,
        String residencyId,
        String userId,
        String username,
        String apartmentId,
        ApartmentStatus apartmentStatus
) {
}
