package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResidentProfileResponse(
        Personal personal,
        Account account,
        Apartment apartment,
        List<Housemate> housemates,
        List<Vehicle> vehicles
) {
    public record Personal(
            String fullName,
            LocalDate birthDate,
            String phone,
            String email,
            String nationalIdMasked
    ) {
    }

    public record Account(String username) {
    }

    public record Apartment(
            String id,
            String code,
            String towerName,
            int floor,
            BigDecimal areaM2,
            int bedrooms,
            int bathrooms,
            LocalDate residingSince
    ) {
    }

    public record Housemate(
            String residentId,
            String fullName,
            ResidencyRelation relation,
            String phone
    ) {
    }

    public record Vehicle(
            String id,
            VehicleType type,
            String plateNumber,
            String cardNumber
    ) {
    }
}
