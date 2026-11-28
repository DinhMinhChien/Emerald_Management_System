package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.dto.response.CurrentFeeTariffsResponse;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.VehicleQuotaRepository;
import org.example.emeraldmanagementsystem.service.FeeTariffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeeTariffServiceImplement implements FeeTariffService {
    private final FeeTariffRepository feeTariffRepository;
    private final VehicleQuotaRepository vehicleQuotaRepository;

    @Override
    @Transactional(readOnly = true)
    public CurrentFeeTariffsResponse getCurrent() {
        LocalDate asOf = LocalDate.now();
        Map<FeeType, List<FeeTariff>> tariffsByType = new EnumMap<>(FeeType.class);
        for (FeeType feeType : FeeType.values()) {
            tariffsByType.put(
                    feeType,
                    feeTariffRepository.findCurrentByFeeType(feeType, asOf)
            );
        }

        FeeTariff management = first(tariffsByType.get(FeeType.MANAGEMENT));
        FeeTariff motorbike = first(tariffsByType.get(FeeType.PARKING_MOTORBIKE));
        FeeTariff car = first(tariffsByType.get(FeeType.PARKING_CAR));

        List<CurrentFeeTariffsResponse.ServiceTier> serviceTiers = tariffsByType
                .get(FeeType.SERVICE).stream()
                .filter(tariff -> tariff.getTierName() != null)
                .sorted(Comparator.comparing(
                        FeeTariff::getMinAreaM2,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(FeeTariff::getEffectiveFrom, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(tariff -> new CurrentFeeTariffsResponse.ServiceTier(
                        tariff.getId(),
                        tariff.getTierName(),
                        tariff.getMinAreaM2(),
                        tariff.getMaxAreaM2(),
                        tariff.getAmount()
                ))
                .toList();

        List<CurrentFeeTariffsResponse.VehicleQuotaItem> vehicleQuotas =
                vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc().stream()
                        .map(quota -> new CurrentFeeTariffsResponse.VehicleQuotaItem(
                                quota.getVehicleType(),
                                quota.getMaxPerApartment()
                        ))
                        .toList();

        return new CurrentFeeTariffsResponse(
                asOf,
                management == null
                        ? null
                        : new CurrentFeeTariffsResponse.ManagementTariff(
                                management.getId(),
                                management.getAmount()
                        ),
                serviceTiers,
                motorbike == null
                        ? null
                        : new CurrentFeeTariffsResponse.ParkingTariff(
                                motorbike.getId(),
                                motorbike.getAmount()
                        ),
                car == null
                        ? null
                        : new CurrentFeeTariffsResponse.ParkingTariff(
                                car.getId(),
                                car.getAmount()
                        ),
                vehicleQuotas
        );
    }

    private FeeTariff first(List<FeeTariff> tariffs) {
        return tariffs == null
                ? null
                : tariffs.stream()
                        .filter(tariff -> tariff.getAmount() != null)
                        .findFirst()
                        .orElse(null);
    }
}
