package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;

import java.time.LocalDate;

public record HouseholdMemberCreateRequest(
        @NotBlank(message = "fullName không được để trống") String fullName,
        @NotNull(message = "relation không được để trống") ResidencyRelation relation,
        LocalDate birthDate,
        String phone,
        String nationalId
) {
}
