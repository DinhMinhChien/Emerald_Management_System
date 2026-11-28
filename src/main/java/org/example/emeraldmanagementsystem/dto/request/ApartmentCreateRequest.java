package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ApartmentCreateRequest(
        @NotBlank(message = "towerId không được để trống")
        String towerId,
        @NotBlank(message = "code không được để trống")
        String code,
        @NotNull(message = "floor không được để trống")
        Integer floor,
        @NotNull(message = "areaM2 không được để trống")
        @DecimalMin(value = "0", inclusive = false, message = "areaM2 phải lớn hơn 0")
        BigDecimal areaM2,
        @NotNull(message = "bedrooms không được để trống")
        Integer bedrooms,
        @NotNull(message = "bathrooms không được để trống")
        Integer bathrooms
) {
}
