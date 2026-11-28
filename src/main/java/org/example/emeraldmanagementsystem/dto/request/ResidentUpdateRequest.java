package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.emeraldmanagementsystem.common.enums.Gender;

import java.time.LocalDate;

public record ResidentUpdateRequest(
        @NotBlank(message = "fullName không được để trống")
        String fullName,
        LocalDate birthDate,
        Gender gender,
        String phone,
        @Email(message = "email không hợp lệ")
        String email,
        String nationalId
) {
}
