package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;

import java.time.LocalDate;
import java.util.List;

public record ApartmentVehiclesResponse(
        List<Quota> quotas,
        ParkingFee parkingFee,
        List<VehicleItem> items
) {
    public record Quota(VehicleType type, Integer max, long used) {
    }

    public record ParkingFee(ParkingFeeItem motorbike, ParkingFeeItem car, Long totalAmount) {
    }

    public record ParkingFeeItem(long count, Long unitAmount, Long amount) {
    }

    public record VehicleItem(
            String id,
            VehicleType type,
            String plateNumber,
            String cardNumber,
            ResidentUser user,
            LocalDate issuedAt,
            LocalDate revokedAt,
            VehicleStatus status
    ) {
    }

    public record ResidentUser(String id, String fullName) {
    }
}
