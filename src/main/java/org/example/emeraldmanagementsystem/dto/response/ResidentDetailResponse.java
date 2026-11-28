package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ResidentDetailResponse(
        String id,
        String fullName,
        LocalDate birthDate,
        Gender gender,
        String phone,
        String email,
        String nationalIdMasked,
        Account account,
        CurrentResidency currentResidency,
        List<ResidencyHistoryItem> residencyHistory
) {
    public record Account(
            String userId,
            String username,
            String status,
            boolean mustChangePassword,
            OffsetDateTime createdAt,
            OffsetDateTime lastLoginAt
    ) {
    }

    public record Apartment(String id, String code, String towerCode, String towerName) {
    }

    public record CurrentResidency(
            String residencyId,
            Apartment apartment,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            List<Housemate> housemates
    ) {
    }

    public record Housemate(String id, String fullName, ResidencyRelation relation) {
    }

    public record ResidencyHistoryItem(
            String residencyId,
            Apartment apartment,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            LocalDate endDate,
            ResidencyEndReason endReason
    ) {
    }
}
