package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;

import java.time.LocalDate;

public record HouseholdMemberCreateResponse(
        String residentId,
        String residencyId,
        String fullName,
        ResidencyRelation relation,
        LocalDate startDate
) {
}
