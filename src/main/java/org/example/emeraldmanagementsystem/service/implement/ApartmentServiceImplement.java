package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.request.ApartmentCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.VehicleCreateRequest;
import org.example.emeraldmanagementsystem.dto.request.VehicleRevokeRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentResidenciesResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.entity.Vehicle;
import org.example.emeraldmanagementsystem.entity.VehicleQuota;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleQuotaRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.example.emeraldmanagementsystem.service.ApartmentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartmentServiceImplement implements ApartmentService {
    private final ApartmentRepository apartmentRepository;
    private final ResidencyRepository residencyRepository;
    private final ResidentRepository residentRepository;
    private final InvoiceRepository invoiceRepository;
    private final TowerRepository towerRepository;
    private final FeeTariffRepository feeTariffRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleQuotaRepository vehicleQuotaRepository;

    @Override
    @Transactional(readOnly = true)
    public ApartmentListResponse getApartments(
            String query,
            String towerId,
            ApartmentStatus status,
            Boolean hasHead,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedQuery = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase() + "%";
        Page<Apartment> apartmentPage = apartmentRepository.searchApartments(
                normalizedQuery,
                towerId,
                status,
                hasHead,
                ResidencyRole.HEAD,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"))
        );

        List<Apartment> apartments = apartmentPage.getContent();
        List<String> apartmentIds = apartments.stream().map(Apartment::getId).toList();
        Map<String, ResidencyRepository.ActiveHeadProjection> headsByApartmentId =
                apartmentIds.isEmpty()
                        ? Map.of()
                        : residencyRepository.findActiveHeadDetailsByApartmentIds(apartmentIds).stream()
                                .collect(Collectors.toMap(
                                        ResidencyRepository.ActiveHeadProjection::getApartmentId,
                                        Function.identity()
                                ));
        Map<String, InvoiceRepository.ApartmentDebtProjection> debtsByApartmentId =
                apartmentIds.isEmpty()
                        ? Map.of()
                        : invoiceRepository.findApartmentDebtSummaries(
                                        apartmentIds,
                                        InvoiceStatus.PENDING,
                                        LocalDate.now()
                                ).stream()
                                .collect(Collectors.toMap(
                                        InvoiceRepository.ApartmentDebtProjection::getApartmentId,
                                        Function.identity()
                                ));
        Map<String, Tower> towersById = towerRepository.findAllById(
                        apartments.stream().map(Apartment::getTowerId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Tower::getId, Function.identity()));

        List<ApartmentListResponse.ApartmentItem> items = apartments.stream()
                .map(apartment -> toApartmentItem(apartment, headsByApartmentId, debtsByApartmentId, towersById))
                .toList();
        return new ApartmentListResponse(items, apartmentPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional
    public ApartmentDetailResponse createApartment(ApartmentCreateRequest request) {
        if (request.areaM2() == null || request.areaM2().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        Tower tower = towerRepository.findByIdAndDeletedFalse(request.towerId())
                .orElseThrow(() -> new BusinessException(404, "TOWER_NOT_FOUND"));
        if (apartmentRepository.existsByCode(request.code())) {
            throw new BusinessException(409, "APARTMENT_CODE_DUPLICATED");
        }

        Apartment apartment = new Apartment();
        apartment.setTowerId(tower.getId());
        apartment.setCode(request.code());
        apartment.setFloor(request.floor());
        apartment.setAreaM2(request.areaM2());
        apartment.setBedrooms(request.bedrooms());
        apartment.setBathrooms(request.bathrooms());
        apartment.setStatus(ApartmentStatus.VACANT);

        try {
            apartmentRepository.saveAndFlush(apartment);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateKeyViolation(exception)) {
                throw new BusinessException(409, "APARTMENT_CODE_DUPLICATED");
            }
            throw exception;
        }

        return new ApartmentDetailResponse(
                apartment.getId(),
                apartment.getCode(),
                new ApartmentDetailResponse.Tower(tower.getId(), tower.getCode(), tower.getName()),
                apartment.getFloor(),
                apartment.getAreaM2(),
                apartment.getBedrooms(),
                apartment.getBathrooms(),
                apartment.getStatus(),
                resolveServiceTierName(apartment.getAreaM2()),
                null,
                0,
                new ApartmentDetailResponse.Debt(0, 0, 0, 0),
                List.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ApartmentDetailResponse getApartmentDetails(String apartmentId) {
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(apartmentId)
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        Tower tower = towerRepository.findByIdAndDeletedFalse(apartment.getTowerId())
                .orElseThrow(() -> new BusinessException(404, "TOWER_NOT_FOUND"));
        List<ResidencyRepository.ResidencyDetailProjection> residencies =
                residencyRepository.findResidencyDetailsByApartmentId(apartmentId, null);

        ApartmentDetailResponse.Head head = residencies.stream()
                .filter(residency -> residency.getRole() == ResidencyRole.HEAD && residency.getEndDate() == null)
                .findFirst()
                .map(residency -> new ApartmentDetailResponse.Head(
                        residency.getResidentId(),
                        residency.getFullName(),
                        residency.getPhone(),
                        residency.getStartDate()
                ))
                .orElse(null);
        List<ApartmentDetailResponse.ResidencyHistoryItem> residencyHistory = residencies.stream()
                .map(residency -> new ApartmentDetailResponse.ResidencyHistoryItem(
                        residency.getResidencyId(),
                        new ApartmentDetailResponse.Resident(residency.getResidentId(), residency.getFullName()),
                        residency.getRole(),
                        residency.getRelation(),
                        residency.getStartDate(),
                        residency.getEndDate(),
                        residency.getEndReason()
                ))
                .toList();

        InvoiceRepository.ApartmentDebtDetailProjection debt =
                invoiceRepository.findDebtSummaryByApartmentId(
                        apartmentId,
                        InvoiceStatus.PENDING,
                        LocalDate.now()
                );

        return new ApartmentDetailResponse(
                apartment.getId(),
                apartment.getCode(),
                new ApartmentDetailResponse.Tower(tower.getId(), tower.getCode(), tower.getName()),
                apartment.getFloor(),
                apartment.getAreaM2(),
                apartment.getBedrooms(),
                apartment.getBathrooms(),
                apartment.getStatus(),
                resolveServiceTierName(apartment.getAreaM2()),
                head,
                residencies.stream().filter(residency -> residency.getEndDate() == null).count(),
                new ApartmentDetailResponse.Debt(
                        debt.getTotalAmount(),
                        debt.getPendingInvoiceCount(),
                        debt.getOverdueInvoiceCount(),
                        debt.getOverdueAmount()
                ),
                residencyHistory
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ApartmentResidenciesResponse getApartmentResidencies(String apartmentId, Boolean current) {
        apartmentRepository.findByIdAndDeletedFalse(apartmentId)
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));

        List<ResidencyRepository.ResidencyDetailProjection> residencies =
                residencyRepository.findResidencyDetailsByApartmentId(apartmentId, current);
        List<String> residentIds = residencies.stream()
                .map(ResidencyRepository.ResidencyDetailProjection::getResidentId)
                .distinct()
                .toList();
        Set<String> residentIdsWithAccounts = residentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(userRepository.findResidentIdsWithAccounts(residentIds));
        List<ApartmentResidenciesResponse.ResidencyItem> items = residencies.stream()
                .map(residency -> new ApartmentResidenciesResponse.ResidencyItem(
                        residency.getResidencyId(),
                        new ApartmentResidenciesResponse.Resident(
                                residency.getResidentId(),
                                residency.getFullName(),
                                residency.getBirthDate(),
                                residency.getGender(),
                                residency.getPhone(),
                                residentIdsWithAccounts.contains(residency.getResidentId())
                        ),
                        residency.getRole(),
                        residency.getRelation(),
                        residency.getStartDate(),
                        residency.getEndDate(),
                        residency.getEndReason()
                ))
                .toList();
        return new ApartmentResidenciesResponse(items);
    }

    @Override
    @Transactional(readOnly = true)
    public ApartmentVehiclesResponse getApartmentVehicles(String apartmentId, String status) {
        apartmentRepository.findByIdAndDeletedFalse(apartmentId)
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));

        String normalizedStatus = status == null ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        VehicleStatus vehicleStatus = switch (normalizedStatus) {
            case "ACTIVE" -> VehicleStatus.ACTIVE;
            case "ALL" -> null;
            default -> throw new BusinessException(400, "VALIDATION_ERROR");
        };
        List<VehicleRepository.VehicleItemProjection> vehicles =
                vehicleRepository.findVehicleItemsByApartmentId(apartmentId, vehicleStatus);
        EnumMap<VehicleType, Long> activeCounts = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            activeCounts.put(type, vehicles.stream()
                    .filter(vehicle -> vehicle.getType() == type && vehicle.getStatus() == VehicleStatus.ACTIVE)
                    .count());
        }

        Map<VehicleType, Integer> maxPerApartment = new EnumMap<>(VehicleType.class);
        for (VehicleQuota quota : vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc()) {
            maxPerApartment.put(quota.getVehicleType(), quota.getMaxPerApartment());
        }
        List<ApartmentVehiclesResponse.Quota> quotas = Arrays.stream(VehicleType.values())
                .map(type -> new ApartmentVehiclesResponse.Quota(
                        type,
                        maxPerApartment.get(type),
                        activeCounts.get(type)
                ))
                .toList();

        Map<FeeType, Long> unitAmounts = new EnumMap<>(FeeType.class);
        for (FeeType feeType : List.of(FeeType.PARKING_MOTORBIKE, FeeType.PARKING_CAR)) {
            feeTariffRepository.findCurrentByFeeType(feeType, LocalDate.now()).stream()
                    .findFirst()
                    .map(FeeTariff::getAmount)
                    .ifPresent(amount -> unitAmounts.put(feeType, amount));
        }
        ApartmentVehiclesResponse.ParkingFeeItem motorbikeFee = toParkingFeeItem(
                activeCounts.get(VehicleType.MOTORBIKE),
                unitAmounts.get(FeeType.PARKING_MOTORBIKE)
        );
        ApartmentVehiclesResponse.ParkingFeeItem carFee = toParkingFeeItem(
                activeCounts.get(VehicleType.CAR),
                unitAmounts.get(FeeType.PARKING_CAR)
        );
        Long totalParkingAmount = motorbikeFee == null || carFee == null
                ? null
                : motorbikeFee.amount() + carFee.amount();

        List<ApartmentVehiclesResponse.VehicleItem> items = vehicles.stream()
                .map(vehicle -> new ApartmentVehiclesResponse.VehicleItem(
                        vehicle.getId(),
                        vehicle.getType(),
                        vehicle.getPlateNumber(),
                        vehicle.getCardNumber(),
                        vehicle.getResidentId() == null
                                ? null
                                : new ApartmentVehiclesResponse.ResidentUser(
                                        vehicle.getResidentId(),
                                        vehicle.getResidentFullName()
                                ),
                        vehicle.getIssuedAt(),
                        vehicle.getRevokedAt(),
                        vehicle.getStatus()
                ))
                .toList();
        return new ApartmentVehiclesResponse(
                quotas,
                new ApartmentVehiclesResponse.ParkingFee(motorbikeFee, carFee, totalParkingAmount),
                items
        );
    }

    @Override
    @Transactional
    public ApartmentVehiclesResponse.VehicleItem createVehicle(String apartmentId, VehicleCreateRequest request) {
        if (request == null || request.type() == null || isBlank(request.plateNumber())
                || isBlank(request.cardNumber()) || isBlank(request.userResidentId())) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        apartmentRepository.findByIdAndDeletedFalse(apartmentId)
                .orElseThrow(() -> new BusinessException(404, "APARTMENT_NOT_FOUND"));
        if (!residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                apartmentId,
                request.userResidentId()
        )) {
            throw new BusinessException(422, "USER_NOT_IN_APARTMENT");
        }
        Resident resident = residentRepository.findByIdAndDeletedFalse(request.userResidentId())
                .orElseThrow(() -> new BusinessException(422, "USER_NOT_IN_APARTMENT"));

        LocalDate issuedAt = request.issuedAt() == null ? LocalDate.now() : request.issuedAt();
        if (issuedAt.isAfter(LocalDate.now())) {
            throw new BusinessException(422, "ISSUED_AT_IN_FUTURE");
        }

        String plateNumber = request.plateNumber().trim().toUpperCase(Locale.ROOT);
        if (vehicleRepository.existsByActivePlateNumberAndDeletedFalse(plateNumber)) {
            throw new BusinessException(409, "PLATE_NUMBER_DUPLICATED");
        }
        if (vehicleRepository.existsByActiveCardNumberAndDeletedFalse(request.cardNumber())) {
            throw new BusinessException(409, "CARD_NUMBER_DUPLICATED");
        }

        VehicleQuota quota = vehicleQuotaRepository.findByVehicleTypeAndDeletedFalse(request.type())
                .orElseThrow(() -> new BusinessException(500, "VEHICLE_QUOTA_NOT_CONFIGURED"));
        long used = vehicleRepository.countByApartmentIdAndTypeAndStatusAndDeletedFalse(
                apartmentId,
                request.type(),
                VehicleStatus.ACTIVE
        );
        if (used >= quota.getMaxPerApartment()) {
            throw new BusinessException(
                    422,
                    "VEHICLE_QUOTA_EXCEEDED\ntype=" + request.type()
                            + "\nmax=" + quota.getMaxPerApartment()
                            + "\nused=" + used
            );
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setApartmentId(apartmentId);
        vehicle.setUserResidentId(resident.getId());
        vehicle.setType(request.type());
        vehicle.setPlateNumber(plateNumber);
        vehicle.setCardNumber(request.cardNumber());
        vehicle.setIssuedAt(issuedAt);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setRevokedAt(null);

        try {
            vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateKeyViolation(exception)) {
                String duplicateErrorCode = getVehicleDuplicateErrorCode(
                        exception,
                        plateNumber,
                        request.cardNumber()
                );
                if (duplicateErrorCode != null) {
                    throw new BusinessException(409, duplicateErrorCode);
                }
            }
            throw exception;
        }

        return new ApartmentVehiclesResponse.VehicleItem(
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getPlateNumber(),
                vehicle.getCardNumber(),
                new ApartmentVehiclesResponse.ResidentUser(resident.getId(), resident.getFullName()),
                vehicle.getIssuedAt(),
                vehicle.getRevokedAt(),
                vehicle.getStatus()
        );
    }

    @Override
    @Transactional
    public ApartmentVehiclesResponse.VehicleItem revokeVehicle(String vehicleId, VehicleRevokeRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndDeletedFalse(vehicleId)
                .orElseThrow(() -> new BusinessException(404, "VEHICLE_NOT_FOUND"));
        if (vehicle.getStatus() == VehicleStatus.REVOKED) {
            throw new BusinessException(409, "VEHICLE_ALREADY_REVOKED");
        }

        LocalDate today = LocalDate.now();
        LocalDate revokedAt = request == null || request.revokedAt() == null
                ? today
                : request.revokedAt();
        if (vehicle.getIssuedAt() == null
                || revokedAt.isBefore(vehicle.getIssuedAt())
                || revokedAt.isAfter(today)) {
            throw new BusinessException(422, "INVALID_REVOKED_DATE");
        }

        vehicle.setStatus(VehicleStatus.REVOKED);
        vehicle.setRevokedAt(revokedAt);
        vehicleRepository.saveAndFlush(vehicle);

        ApartmentVehiclesResponse.ResidentUser residentUser = residentRepository
                .findByIdAndDeletedFalse(vehicle.getUserResidentId())
                .map(resident -> new ApartmentVehiclesResponse.ResidentUser(
                        resident.getId(),
                        resident.getFullName()
                ))
                .orElse(null);
        return new ApartmentVehiclesResponse.VehicleItem(
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getPlateNumber(),
                vehicle.getCardNumber(),
                residentUser,
                vehicle.getIssuedAt(),
                vehicle.getRevokedAt(),
                vehicle.getStatus()
        );
    }

    private ApartmentVehiclesResponse.ParkingFeeItem toParkingFeeItem(long count, Long unitAmount) {
        if (unitAmount == null) {
            return null;
        }
        return new ApartmentVehiclesResponse.ParkingFeeItem(count, unitAmount, Math.multiplyExact(count, unitAmount));
    }

    private String resolveServiceTierName(BigDecimal areaM2) {
        return feeTariffRepository.findCurrentByFeeType(FeeType.SERVICE, LocalDate.now()).stream()
                .filter(tariff -> includesArea(tariff, areaM2))
                .map(FeeTariff::getTierName)
                .findFirst()
                .orElse(null);
    }

    private boolean includesArea(FeeTariff tariff, BigDecimal areaM2) {
        return tariff.getMinAreaM2() != null
                && areaM2.compareTo(tariff.getMinAreaM2()) >= 0
                && (tariff.getMaxAreaM2() == null || areaM2.compareTo(tariff.getMaxAreaM2()) < 0);
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062;
    }

    private String getVehicleDuplicateErrorCode(
            DataIntegrityViolationException exception,
            String plateNumber,
            String cardNumber
    ) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null) {
            String normalizedMessage = message.toLowerCase(Locale.ROOT);
            if (normalizedMessage.contains("plate")) {
                return "PLATE_NUMBER_DUPLICATED";
            }
            if (normalizedMessage.contains("card")) {
                return "CARD_NUMBER_DUPLICATED";
            }
        }
        if (vehicleRepository.existsByActivePlateNumberAndDeletedFalse(plateNumber)) {
            return "PLATE_NUMBER_DUPLICATED";
        }
        if (vehicleRepository.existsByActiveCardNumberAndDeletedFalse(cardNumber)) {
            return "CARD_NUMBER_DUPLICATED";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ApartmentListResponse.ApartmentItem toApartmentItem(
            Apartment apartment,
            Map<String, ResidencyRepository.ActiveHeadProjection> headsByApartmentId,
            Map<String, InvoiceRepository.ApartmentDebtProjection> debtsByApartmentId,
            Map<String, Tower> towersById
    ) {
        ResidencyRepository.ActiveHeadProjection headProjection = headsByApartmentId.get(apartment.getId());
        ApartmentListResponse.Head head = headProjection == null
                ? null
                : new ApartmentListResponse.Head(
                        headProjection.getResidentId(),
                        headProjection.getFullName(),
                        headProjection.getPhone()
                );

        InvoiceRepository.ApartmentDebtProjection debt = debtsByApartmentId.get(apartment.getId());
        Tower tower = towersById.get(apartment.getTowerId());
        if (tower == null) {
            throw new BusinessException(404, "TOWER_NOT_FOUND");
        }
        ApartmentListResponse.Tower towerResponse =
                new ApartmentListResponse.Tower(tower.getId(), tower.getCode(), tower.getName());

        return new ApartmentListResponse.ApartmentItem(
                apartment.getId(),
                apartment.getCode(),
                towerResponse,
                apartment.getFloor(),
                apartment.getAreaM2(),
                apartment.getStatus(),
                head,
                debt == null || debt.getDebtAmount() == null ? 0 : debt.getDebtAmount(),
                debt == null || debt.getOverdueInvoiceCount() == null ? 0 : debt.getOverdueInvoiceCount()
        );
    }
}
