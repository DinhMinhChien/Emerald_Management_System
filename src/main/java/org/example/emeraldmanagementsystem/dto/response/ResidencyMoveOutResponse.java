package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.VehicleMoveOutAction;

import java.time.LocalDate;
import java.util.List;

public record ResidencyMoveOutResponse(
        String residencyId,
        Resident resident,
        LocalDate endDate,
        ResidencyEndReason endReason,
        Apartment apartment,
        boolean oldHeadAccountLocked,
        NewHead newHead,
        List<VehicleResult> vehicleResults
) {
    public record Resident(String id, String fullName) {
    }

    public record Apartment(String id, String code, ApartmentStatus status) {
    }

    public record NewHead(
            String residentId,
            String residencyId,
            boolean accountCreated,
            String username
    ) {
    }

    public record VehicleResult(
            String vehicleId,
            VehicleMoveOutAction action,
            String transferToResidentId
    ) {
    }
}
