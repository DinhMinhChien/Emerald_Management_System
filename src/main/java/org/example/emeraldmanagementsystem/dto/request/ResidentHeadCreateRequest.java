package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.emeraldmanagementsystem.common.enums.Gender;

import java.time.LocalDate;

public record ResidentHeadCreateRequest(
        @NotBlank(message = "fullName không được để trống")
        String fullName,
        LocalDate birthDate,
        Gender gender,
        String phone,
        @Email(message = "email không hợp lệ")
        String email,
        String nationalId,
        @NotBlank(message = "apartmentId không được để trống")
        String apartmentId,
        @NotNull(message = "startDate không được để trống")
        LocalDate startDate,
        @NotBlank(message = "username không được để trống")
        @Size(max = 20, message = "username không được vượt quá 20 ký tự")
        String username,
        @NotBlank(message = "temporaryPassword không được để trống")
        String temporaryPassword
) {
}
