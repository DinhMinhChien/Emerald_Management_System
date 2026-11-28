package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;

import java.time.LocalDate;

public record VehicleCreateRequest(
        @NotNull(message = "type không được để trống")
        VehicleType type,
        @NotBlank(message = "plateNumber không được để trống")
        String plateNumber,
        @NotBlank(message = "cardNumber không được để trống")
        String cardNumber,
        @NotBlank(message = "userResidentId không được để trống")
        String userResidentId,
        LocalDate issuedAt
) {
}
