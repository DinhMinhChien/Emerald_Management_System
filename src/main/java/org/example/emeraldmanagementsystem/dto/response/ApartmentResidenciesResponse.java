package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;

import java.time.LocalDate;
import java.util.List;

public record ApartmentResidenciesResponse(List<ResidencyItem> items) {
    public record ResidencyItem(
            String residencyId,
            Resident resident,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            LocalDate endDate,
            ResidencyEndReason endReason
    ) {
    }

    public record Resident(
            String id,
            String fullName,
            LocalDate birthDate,
            Gender gender,
            String phone,
            boolean hasAccount
    ) {
    }
}
