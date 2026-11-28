package org.example.emeraldmanagementsystem.dto.response;

import org.example.emeraldmanagementsystem.common.enums.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CurrentFeeTariffsResponse(
        LocalDate asOf,
        ManagementTariff management,
        List<ServiceTier> serviceTiers,
        ParkingTariff parkingMotorbike,
        ParkingTariff parkingCar,
        List<VehicleQuotaItem> vehicleQuotas
) {
    public record ManagementTariff(String tariffId, Long amount) {
    }

    public record ServiceTier(
            String tariffId,
            String tierName,
            BigDecimal minAreaM2,
            BigDecimal maxAreaM2,
            Long amount
    ) {
    }

    public record ParkingTariff(String tariffId, Long amount) {
    }

    public record VehicleQuotaItem(VehicleType type, int maxPerApartment) {
    }
}
