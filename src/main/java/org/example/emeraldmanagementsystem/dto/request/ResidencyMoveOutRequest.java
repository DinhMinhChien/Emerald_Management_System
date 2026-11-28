package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotNull;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.VehicleMoveOutAction;

import java.time.LocalDate;
import java.util.List;

public record ResidencyMoveOutRequest(
        @NotNull(message = "endDate không được để trống")
        LocalDate endDate,
        @NotNull(message = "endReason không được để trống")
        ResidencyEndReason endReason,
        NewHead newHead,
        List<VehicleAction> vehicleActions
) {
    public record NewHead(
            String residentId,
            String username,
            String temporaryPassword
    ) {
    }

    public record VehicleAction(
            String vehicleId,
            VehicleMoveOutAction action,
            String transferToResidentId
    ) {
    }
}
