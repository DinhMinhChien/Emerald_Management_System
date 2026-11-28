package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.response.CurrentFeeTariffsResponse;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.entity.VehicleQuota;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.VehicleQuotaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeTariffServiceImplementTest {
    @Mock
    private FeeTariffRepository feeTariffRepository;
    @Mock
    private VehicleQuotaRepository vehicleQuotaRepository;
    @InjectMocks
    private FeeTariffServiceImplement feeTariffService;

    @Test
    void returnsCurrentTariffsAndVehicleQuotas() {
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.MANAGEMENT), any(LocalDate.class)))
                .thenReturn(List.of(tariff("management", FeeType.MANAGEMENT, null, null, null, 1_200_000)));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.SERVICE), any(LocalDate.class)))
                .thenReturn(List.of(
                        tariff("service-1", FeeType.SERVICE, "Mức 1", "0", "60", 1_500_000),
                        tariff("service-2", FeeType.SERVICE, "Mức 2", "60", null, 2_050_000)
                ));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.PARKING_MOTORBIKE), any(LocalDate.class)))
                .thenReturn(List.of(tariff("motorbike", FeeType.PARKING_MOTORBIKE, null, null, null, 100_000)));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.PARKING_CAR), any(LocalDate.class)))
                .thenReturn(List.of(tariff("car", FeeType.PARKING_CAR, null, null, null, 1_200_000)));
        when(vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc())
                .thenReturn(List.of(
                        quota(VehicleType.MOTORBIKE, 3),
                        quota(VehicleType.CAR, 1)
                ));

        CurrentFeeTariffsResponse response = feeTariffService.getCurrent();

        assertEquals(LocalDate.now(), response.asOf());
        assertEquals("management", response.management().tariffId());
        assertEquals(1_200_000L, response.management().amount());
        assertEquals(2, response.serviceTiers().size());
        assertEquals("Mức 1", response.serviceTiers().getFirst().tierName());
        assertEquals(new BigDecimal("60"), response.serviceTiers().getFirst().maxAreaM2());
        assertEquals("motorbike", response.parkingMotorbike().tariffId());
        assertEquals("car", response.parkingCar().tariffId());
        assertEquals(2, response.vehicleQuotas().size());
        assertEquals(3, response.vehicleQuotas().getFirst().maxPerApartment());
    }

    @Test
    void returnsNullTariffGroupsWhenNoCurrentTariffExists() {
        when(feeTariffRepository.findCurrentByFeeType(any(FeeType.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc()).thenReturn(List.of());

        CurrentFeeTariffsResponse response = feeTariffService.getCurrent();

        assertNull(response.management());
        assertEquals(0, response.serviceTiers().size());
        assertNull(response.parkingMotorbike());
        assertNull(response.parkingCar());
        assertEquals(0, response.vehicleQuotas().size());
    }

    private FeeTariff tariff(
            String id,
            FeeType feeType,
            String tierName,
            String minArea,
            String maxArea,
            long amount
    ) {
        FeeTariff tariff = new FeeTariff();
        tariff.setId(id);
        tariff.setFeeType(feeType);
        tariff.setTierName(tierName);
        tariff.setMinAreaM2(minArea == null ? null : new BigDecimal(minArea));
        tariff.setMaxAreaM2(maxArea == null ? null : new BigDecimal(maxArea));
        tariff.setAmount(amount);
        tariff.setEffectiveFrom(LocalDate.now().minusDays(1));
        return tariff;
    }

    private VehicleQuota quota(VehicleType type, int maxPerApartment) {
        VehicleQuota quota = new VehicleQuota();
        quota.setVehicleType(type);
        quota.setMaxPerApartment(maxPerApartment);
        return quota;
    }
}
