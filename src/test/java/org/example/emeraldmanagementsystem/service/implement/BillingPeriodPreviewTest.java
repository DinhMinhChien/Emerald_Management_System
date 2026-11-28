package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.request.BillingPeriodIssueRequest;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodPreviewResponse;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodIssueResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceItemRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPeriodPreviewTest {
    @Mock
    private BillingPeriodRepository billingPeriodRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private TowerRepository towerRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private FeeTariffRepository feeTariffRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private BillingPeriodServiceImplement billingPeriodService;

    @Test
    void calculatesWholePeriodTotalsBeforeApplyingSearchAndPagination() {
        Apartment first = apartment("apt-1", "A-1205", ApartmentStatus.OCCUPIED, "tower-1", "72.50");
        Apartment noHead = apartment("apt-2", "B-0305", ApartmentStatus.REPAIRING, "tower-1", "50");
        Apartment vacant = apartment("apt-3", "C-0101", ApartmentStatus.VACANT, "tower-1", "50");
        Apartment second = apartment("apt-4", "D-0202", ApartmentStatus.OCCUPIED, "tower-2", "55");
        Tower tower1 = tower("tower-1", "A");
        Tower tower2 = tower("tower-2", "B");

        when(billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(2026, 3)).thenReturn(false);
        when(apartmentRepository.findAllByDeletedFalseOrderByCodeAsc())
                .thenReturn(List.of(first, noHead, vacant, second));
        when(residencyRepository.findActiveHeadDetailsByApartmentIds(any()))
                .thenReturn(List.of(head("apt-1", "Resident One"), head("apt-4", "Resident Two")));
        when(vehicleRepository.findActiveVehicleCountsByApartmentIds(any()))
                .thenReturn(List.of(vehicleCount("apt-1", VehicleType.MOTORBIKE, 2)));
        when(towerRepository.findAllByDeletedFalseOrderByCodeAsc()).thenReturn(List.of(tower1, tower2));
        when(feeTariffRepository.findCurrentByFeeType(any(), any())).thenAnswer(invocation ->
                tariffs().get(invocation.getArgument(0))
        );

        BillingPeriodPreviewResponse response = billingPeriodService.preview(
                2026,
                3,
                "a-",
                "tower-1",
                0,
                1
        );

        assertEquals(2, response.invoiceCount());
        assertEquals(1, response.skippedVacantCount());
        assertEquals(7_200, response.totalAmount());
        assertEquals(1, response.warnings().size());
        assertEquals("NO_ACTIVE_HEAD", response.warnings().getFirst().code());
        assertEquals(1, response.apartments().total());
        assertEquals(1, response.apartments().items().size());
        assertEquals("A-1205", response.apartments().items().getFirst().code());
        assertEquals(4_200, response.apartments().items().getFirst().totalAmount());
        assertEquals("Mức 2 (căn 72.5 m²)", response.apartments().items().getFirst().items().get(1).description());
    }

    @Test
    void rejectsAnExistingPeriod() {
        when(billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(2026, 3)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> billingPeriodService.preview(2026, 3, null, null, 0, 20)
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("PERIOD_ALREADY_EXISTS", exception.getMessage());
    }

    @Test
    void rejectsMissingRequiredTariffs() {
        Apartment occupied = apartment("apt-1", "A-1205", ApartmentStatus.OCCUPIED, "tower-1", "72.50");
        when(billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(2026, 3)).thenReturn(false);
        when(apartmentRepository.findAllByDeletedFalseOrderByCodeAsc()).thenReturn(List.of(occupied));
        when(residencyRepository.findActiveHeadDetailsByApartmentIds(any()))
                .thenReturn(List.of(head("apt-1", "Resident One")));
        when(vehicleRepository.findActiveVehicleCountsByApartmentIds(any())).thenReturn(List.of());
        when(towerRepository.findAllByDeletedFalseOrderByCodeAsc()).thenReturn(List.of());
        when(feeTariffRepository.findCurrentByFeeType(any(), any())).thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> billingPeriodService.preview(2026, 3, null, null, 0, 20)
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("TARIFF_NOT_CONFIGURED", exception.getMessage());
    }

    @Test
    void rejectsInvalidMonth() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> billingPeriodService.preview(2026, 13, null, null, 0, 20)
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
    }

    @Test
    void issuesInvoicesForEligibleApartments() {
        Apartment occupied = apartment("apt-1", "A-1205", ApartmentStatus.OCCUPIED, "tower-1", "72.50");
        Apartment vacant = apartment("apt-2", "B-0101", ApartmentStatus.VACANT, "tower-1", "50");
        when(billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(2026, 3)).thenReturn(false);
        when(apartmentRepository.findAllByDeletedFalseOrderByCodeAsc())
                .thenReturn(List.of(occupied, vacant));
        when(residencyRepository.findActiveHeadDetailsByApartmentIds(any()))
                .thenReturn(List.of(head("apt-1", "Resident One")));
        when(vehicleRepository.findActiveVehicleCountsByApartmentIds(any())).thenReturn(List.of(
                vehicleCount("apt-1", VehicleType.MOTORBIKE, 2)
        ));
        when(feeTariffRepository.findCurrentByFeeType(any(), any())).thenAnswer(invocation ->
                tariffs().get(invocation.getArgument(0))
        );
        when(billingPeriodRepository.saveAndFlush(any(BillingPeriod.class))).thenAnswer(invocation -> {
            BillingPeriod period = invocation.getArgument(0);
            period.setId("per-202603");
            return period;
        });
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId("inv-1");
            return invoice;
        });

        BillingPeriodIssueResponse response = billingPeriodService.issue(
                new BillingPeriodIssueRequest(
                        2026,
                        3,
                        LocalDate.now().plusDays(10)
                )
        );

        assertEquals("per-202603", response.periodId());
        assertEquals(1, response.issuedInvoiceCount());
        assertEquals(1, response.skippedVacantCount());
        assertEquals(4_200, response.totalAmount());
        assertEquals("SYSTEM", response.issuedBy());
    }

    @Test
    void rejectsPastDueDate() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> billingPeriodService.issue(new BillingPeriodIssueRequest(
                        2026,
                        3,
                        LocalDate.now().minusDays(1)
                ))
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_DUE_DATE", exception.getMessage());
    }

    private Apartment apartment(
            String id,
            String code,
            ApartmentStatus status,
            String towerId,
            String areaM2
    ) {
        Apartment apartment = new Apartment();
        apartment.setId(id);
        apartment.setCode(code);
        apartment.setStatus(status);
        apartment.setTowerId(towerId);
        apartment.setAreaM2(new BigDecimal(areaM2));
        return apartment;
    }

    private Tower tower(String id, String code) {
        Tower tower = new Tower();
        tower.setId(id);
        tower.setCode(code);
        return tower;
    }

    private ResidencyRepository.ActiveHeadProjection head(String apartmentId, String name) {
        return new ResidencyRepository.ActiveHeadProjection() {
            @Override
            public String getApartmentId() {
                return apartmentId;
            }

            @Override
            public String getResidentId() {
                return "resident-" + apartmentId;
            }

            @Override
            public String getFullName() {
                return name;
            }

            @Override
            public String getPhone() {
                return null;
            }
        };
    }

    private VehicleRepository.ActiveVehicleCountProjection vehicleCount(
            String apartmentId,
            VehicleType type,
            long count
    ) {
        return new VehicleRepository.ActiveVehicleCountProjection() {
            @Override
            public String getApartmentId() {
                return apartmentId;
            }

            @Override
            public VehicleType getType() {
                return type;
            }

            @Override
            public Long getVehicleCount() {
                return count;
            }
        };
    }

    private Map<FeeType, List<FeeTariff>> tariffs() {
        return Map.of(
                FeeType.MANAGEMENT,
                List.of(tariff(FeeType.MANAGEMENT, null, null, null, 1_000)),
                FeeType.SERVICE,
                List.of(
                        tariff(FeeType.SERVICE, "Mức 1", "0", "60", 2_000),
                        tariff(FeeType.SERVICE, "Mức 2", "60", null, 3_000)
                ),
                FeeType.PARKING_MOTORBIKE,
                List.of(tariff(FeeType.PARKING_MOTORBIKE, null, null, null, 100)),
                FeeType.PARKING_CAR,
                List.of(tariff(FeeType.PARKING_CAR, null, null, null, 500))
        );
    }

    private FeeTariff tariff(
            FeeType feeType,
            String tierName,
            String minArea,
            String maxArea,
            long amount
    ) {
        FeeTariff tariff = new FeeTariff();
        tariff.setId("tariff-" + feeType + "-" + tierName);
        tariff.setFeeType(feeType);
        tariff.setTierName(tierName);
        tariff.setMinAreaM2(minArea == null ? null : new BigDecimal(minArea));
        tariff.setMaxAreaM2(maxArea == null ? null : new BigDecimal(maxArea));
        tariff.setAmount(amount);
        tariff.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return tariff;
    }
}
