package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.response.BillingAvailableMonthsResponse;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodPreviewResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.example.emeraldmanagementsystem.service.BillingPeriodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BillingPeriodServiceImplement implements BillingPeriodService {
    private static final int AVAILABLE_MONTHS_HISTORY = 12;

    private final BillingPeriodRepository billingPeriodRepository;
    private final ApartmentRepository apartmentRepository;
    private final ResidencyRepository residencyRepository;
    private final TowerRepository towerRepository;
    private final VehicleRepository vehicleRepository;
    private final FeeTariffRepository feeTariffRepository;

    @Override
    @Transactional(readOnly = true)
    public BillingAvailableMonthsResponse getAvailableMonths() {
        List<BillingPeriod> billingPeriods = billingPeriodRepository.findAllByDeletedFalseOrderByYearAscMonthAsc();
        Set<YearMonth> existingMonths = billingPeriods.stream()
                .map(period -> YearMonth.of(period.getYear(), period.getMonth()))
                .collect(Collectors.toSet());

        BillingAvailableMonthsResponse.DefaultMonth defaultMonth = billingPeriodRepository
                .findFirstByDeletedFalseOrderByYearDescMonthDesc()
                .map(period -> YearMonth.of(period.getYear(), period.getMonth()).plusMonths(1))
                .map(this::toDefaultMonth)
                .orElse(null);

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(AVAILABLE_MONTHS_HISTORY - 1L);
        YearMonth finalMonth = currentMonth.plusMonths(1);
        long monthCount = firstMonth.until(finalMonth, java.time.temporal.ChronoUnit.MONTHS) + 1;
        List<BillingAvailableMonthsResponse.AvailableMonth> items = Stream.iterate(
                        firstMonth,
                        month -> month.plusMonths(1)
                )
                .limit(monthCount)
                .filter(month -> !existingMonths.contains(month))
                .map(this::toAvailableMonth)
                .toList();

        return new BillingAvailableMonthsResponse(defaultMonth, items);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingPeriodPreviewResponse preview(
            Integer year,
            Integer month,
            String query,
            String towerId,
            int page,
            int size
    ) {
        if (year == null || year < 1 || year > 9999
                || month == null || month < 1 || month > 12
                || page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        YearMonth billingMonth;
        try {
            billingMonth = YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(year, month)) {
            throw new BusinessException(409, "PERIOD_ALREADY_EXISTS");
        }

        List<Apartment> apartments = apartmentRepository.findAllByDeletedFalseOrderByCodeAsc();
        List<String> apartmentIds = apartments.stream().map(Apartment::getId).toList();
        Map<String, String> towerIdsByApartmentId = apartments.stream()
                .collect(Collectors.toMap(Apartment::getId, Apartment::getTowerId));
        Map<String, ResidencyRepository.ActiveHeadProjection> headsByApartmentId = apartmentIds.isEmpty()
                ? Map.of()
                : residencyRepository.findActiveHeadDetailsByApartmentIds(apartmentIds).stream()
                        .collect(Collectors.toMap(
                                ResidencyRepository.ActiveHeadProjection::getApartmentId,
                                head -> head
                        ));
        Map<String, Map<VehicleType, Long>> vehiclesByApartmentId = new java.util.HashMap<>();
        if (!apartmentIds.isEmpty()) {
            for (VehicleRepository.ActiveVehicleCountProjection vehicleCount
                    : vehicleRepository.findActiveVehicleCountsByApartmentIds(apartmentIds)) {
                vehiclesByApartmentId
                        .computeIfAbsent(vehicleCount.getApartmentId(), ignored -> new EnumMap<>(VehicleType.class))
                        .put(vehicleCount.getType(), vehicleCount.getVehicleCount());
            }
        }
        Map<String, Tower> towersById = towerRepository.findAllByDeletedFalseOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Tower::getId, tower -> tower));
        Map<FeeType, List<FeeTariff>> tariffsByType = new EnumMap<>(FeeType.class);
        LocalDate tariffDate = billingMonth.atDay(1);
        for (FeeType feeType : FeeType.values()) {
            tariffsByType.put(feeType, feeTariffRepository.findCurrentByFeeType(feeType, tariffDate).stream()
                    .sorted(Comparator.comparing(
                            FeeTariff::getEffectiveFrom,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .toList());
        }

        List<BillingPeriodPreviewResponse.Warning> warnings = new ArrayList<>();
        List<BillingPeriodPreviewResponse.ApartmentItem> invoiceItems = new ArrayList<>();
        long skippedVacantCount = 0;
        long totalAmount = 0;
        for (Apartment apartment : apartments) {
            if (apartment.getStatus() == ApartmentStatus.VACANT) {
                skippedVacantCount++;
                continue;
            }

            ResidencyRepository.ActiveHeadProjection head = headsByApartmentId.get(apartment.getId());
            if (head == null) {
                warnings.add(new BillingPeriodPreviewResponse.Warning(
                        apartment.getCode(),
                        "NO_ACTIVE_HEAD",
                        "Căn không có chủ hộ đang cư trú, sẽ bị bỏ qua"
                ));
                continue;
            }

            Map<VehicleType, Long> vehicleCounts = vehiclesByApartmentId.getOrDefault(
                    apartment.getId(),
                    Map.of()
            );
            List<BillingPeriodPreviewResponse.InvoiceLineItem> lineItems = buildLineItems(
                    apartment,
                    vehicleCounts,
                    tariffsByType
            );
            long apartmentTotal = lineItems.stream()
                    .mapToLong(BillingPeriodPreviewResponse.InvoiceLineItem::amount)
                    .reduce(0L, Math::addExact);
            totalAmount = Math.addExact(totalAmount, apartmentTotal);
            FeeTariff serviceTariff = findServiceTariff(apartment.getAreaM2(), tariffsByType.get(FeeType.SERVICE));
            invoiceItems.add(new BillingPeriodPreviewResponse.ApartmentItem(
                    apartment.getId(),
                    apartment.getCode(),
                    towersById.containsKey(apartment.getTowerId())
                            ? towersById.get(apartment.getTowerId()).getCode()
                            : null,
                    apartment.getAreaM2(),
                    serviceTariff.getTierName(),
                    apartment.getStatus(),
                    head.getFullName(),
                    lineItems,
                    apartmentTotal
            ));
        }

        String normalizedQuery = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        List<BillingPeriodPreviewResponse.ApartmentItem> filteredItems = invoiceItems.stream()
                .filter(item -> normalizedQuery == null || normalizedQuery.isEmpty()
                        || item.code().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .filter(item -> towerId == null || towerId.isBlank()
                        || towerId.equals(towerIdsByApartmentId.get(item.apartmentId())))
                .sorted(Comparator.comparing(
                        BillingPeriodPreviewResponse.ApartmentItem::code,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ).thenComparing(BillingPeriodPreviewResponse.ApartmentItem::apartmentId))
                .toList();
        long startOffset = (long) page * size;
        int start = (int) Math.min(startOffset, filteredItems.size());
        int end = Math.min(start + size, filteredItems.size());

        return new BillingPeriodPreviewResponse(
                year,
                month,
                invoiceItems.size(),
                skippedVacantCount,
                totalAmount,
                warnings,
                new BillingPeriodPreviewResponse.ApartmentPage(
                        filteredItems.subList(start, end),
                        filteredItems.size(),
                        page,
                        size
                )
        );
    }

    private List<BillingPeriodPreviewResponse.InvoiceLineItem> buildLineItems(
            Apartment apartment,
            Map<VehicleType, Long> vehicleCounts,
            Map<FeeType, List<FeeTariff>> tariffsByType
    ) {
        FeeTariff managementTariff = firstTariffWithAmount(tariffsByType.get(FeeType.MANAGEMENT));
        if (managementTariff == null) {
            throw new BusinessException(422, "TARIFF_NOT_CONFIGURED");
        }
        FeeTariff serviceTariff = findServiceTariff(apartment.getAreaM2(), tariffsByType.get(FeeType.SERVICE));
        if (serviceTariff == null || serviceTariff.getAmount() == null || serviceTariff.getTierName() == null) {
            throw new BusinessException(422, "TARIFF_NOT_CONFIGURED");
        }

        List<BillingPeriodPreviewResponse.InvoiceLineItem> items = new ArrayList<>();
        items.add(toLineItem(FeeType.MANAGEMENT, "Phí quản lý", 1, managementTariff));
        items.add(toLineItem(
                FeeType.SERVICE,
                "%s (căn %s m²)".formatted(
                        serviceTariff.getTierName(),
                        formatArea(apartment.getAreaM2())
                ),
                1,
                serviceTariff
        ));
        addParkingItem(
                items,
                FeeType.PARKING_MOTORBIKE,
                vehicleCounts.getOrDefault(VehicleType.MOTORBIKE, 0L),
                "xe máy",
                tariffsByType.get(FeeType.PARKING_MOTORBIKE)
        );
        addParkingItem(
                items,
                FeeType.PARKING_CAR,
                vehicleCounts.getOrDefault(VehicleType.CAR, 0L),
                "ô tô",
                tariffsByType.get(FeeType.PARKING_CAR)
        );
        return List.copyOf(items);
    }

    private void addParkingItem(
            List<BillingPeriodPreviewResponse.InvoiceLineItem> items,
            FeeType feeType,
            long quantity,
            String vehicleLabel,
            List<FeeTariff> tariffs
    ) {
        if (quantity == 0) {
            return;
        }
        FeeTariff tariff = firstTariffWithAmount(tariffs);
        if (tariff == null) {
            throw new BusinessException(422, "TARIFF_NOT_CONFIGURED");
        }
        items.add(toLineItem(
                feeType,
                "%d %s".formatted(quantity, vehicleLabel),
                Math.toIntExact(quantity),
                tariff
        ));
    }

    private BillingPeriodPreviewResponse.InvoiceLineItem toLineItem(
            FeeType feeType,
            String description,
            int quantity,
            FeeTariff tariff
    ) {
        long amount = Math.multiplyExact(quantity, tariff.getAmount());
        return new BillingPeriodPreviewResponse.InvoiceLineItem(
                feeType,
                description,
                quantity,
                tariff.getAmount(),
                amount
        );
    }

    private FeeTariff findServiceTariff(BigDecimal areaM2, List<FeeTariff> tariffs) {
        if (areaM2 == null || tariffs == null) {
            return null;
        }
        return tariffs.stream()
                .filter(tariff -> tariff.getMinAreaM2() != null
                        && areaM2.compareTo(tariff.getMinAreaM2()) >= 0
                        && (tariff.getMaxAreaM2() == null
                        || areaM2.compareTo(tariff.getMaxAreaM2()) < 0))
                .findFirst()
                .orElse(null);
    }

    private FeeTariff firstTariffWithAmount(List<FeeTariff> tariffs) {
        if (tariffs == null) {
            return null;
        }
        return tariffs.stream()
                .filter(tariff -> tariff.getAmount() != null)
                .findFirst()
                .orElse(null);
    }

    private String formatArea(BigDecimal areaM2) {
        return areaM2.stripTrailingZeros().toPlainString();
    }

    private BillingAvailableMonthsResponse.DefaultMonth toDefaultMonth(YearMonth month) {
        return new BillingAvailableMonthsResponse.DefaultMonth(month.getYear(), month.getMonthValue());
    }

    private BillingAvailableMonthsResponse.AvailableMonth toAvailableMonth(YearMonth month) {
        return new BillingAvailableMonthsResponse.AvailableMonth(
                month.getYear(),
                month.getMonthValue(),
                "%02d/%d".formatted(month.getMonthValue(), month.getYear())
        );
    }
}
