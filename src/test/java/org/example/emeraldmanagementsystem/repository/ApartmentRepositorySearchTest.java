package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional(readOnly = true)
class ApartmentRepositorySearchTest {
    @Autowired
    private ApartmentRepository apartmentRepository;
    @Autowired
    private ResidencyRepository residencyRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleQuotaRepository vehicleQuotaRepository;
    @Autowired
    private FeeTariffRepository feeTariffRepository;
    @Autowired
    private BillingPeriodRepository billingPeriodRepository;
    @Autowired
    private ResidentRepository residentRepository;

    @Test
    void executesApartmentSearchWithAndWithoutOptionalFilters() {
        assertNotNull(apartmentRepository.searchApartments(
                null,
                null,
                null,
                null,
                ResidencyRole.HEAD,
                PageRequest.of(0, 20)
        ));
        assertNotNull(apartmentRepository.findAllByDeletedFalseOrderByCodeAsc());

        assertNotNull(apartmentRepository.searchApartments(
                "%a-",
                "nonexistent-tower",
                ApartmentStatus.VACANT,
                false,
                ResidencyRole.HEAD,
                PageRequest.of(0, 20, Sort.by("code").ascending())
        ));
        assertNotNull(residencyRepository.findActiveHeadDetailsByApartmentIds(List.of("nonexistent-apartment")));
        assertNotNull(invoiceRepository.findApartmentDebtSummaries(
                List.of("nonexistent-apartment"),
                InvoiceStatus.PENDING,
                LocalDate.now()
        ));
        assertEquals(
                List.of(),
                residencyRepository.findResidencyDetailsByApartmentId("nonexistent-apartment", null)
        );
        assertEquals(
                List.of(),
                residencyRepository.findResidencyDetailsByApartmentId("nonexistent-apartment", true)
        );
        assertEquals(
                List.of(),
                userRepository.findResidentIdsWithAccounts(List.of("nonexistent-resident"))
        );
        assertNotNull(vehicleRepository.findVehicleItemsByApartmentId("nonexistent-apartment", null));
        assertEquals(Optional.empty(), vehicleRepository.findByIdAndDeletedFalse("nonexistent-vehicle"));
        assertNotNull(vehicleRepository.findVehicleItemsByApartmentId(
                "nonexistent-apartment",
                VehicleStatus.ACTIVE
        ));
        assertNotNull(vehicleRepository.findActiveVehicleCountsByApartmentIds(List.of("nonexistent-apartment")));
        assertFalse(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("NONEXISTENT-PLATE"));
        assertFalse(vehicleRepository.existsByActiveCardNumberAndDeletedFalse("NONEXISTENT-CARD"));
        assertEquals(0L, vehicleRepository.countByApartmentIdAndTypeAndStatusAndDeletedFalse(
                "nonexistent-apartment",
                VehicleType.CAR,
                VehicleStatus.ACTIVE
        ));
        assertEquals(
                Optional.empty(),
                vehicleQuotaRepository.findByVehicleTypeAndDeletedFalse(VehicleType.CAR)
        );
        assertFalse(residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                "nonexistent-apartment",
                "nonexistent-resident"
        ));
        assertNotNull(vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc());
        assertNotNull(feeTariffRepository.findCurrentByFeeType(FeeType.PARKING_MOTORBIKE, LocalDate.now()));
        assertNotNull(billingPeriodRepository.findAllByDeletedFalseOrderByYearAscMonthAsc());
        assertNotNull(billingPeriodRepository.findFirstByDeletedFalseOrderByYearDescMonthDesc());
        assertNotNull(billingPeriodRepository.existsByDeletedFalseAndYearAndMonth(1900, 1));
        assertNotNull(residentRepository.searchResidents(
                null,
                null,
                true,
                PageRequest.of(0, 20, Sort.by("fullName").ascending().and(Sort.by("id").ascending()))
        ));
        assertNotNull(residentRepository.searchResidents(
                "%hoa%",
                "nonexistent-tower",
                false,
                PageRequest.of(0, 20, Sort.by("fullName").ascending().and(Sort.by("id").ascending()))
        ));
        assertNotNull(userRepository.findResidentAccountStatuses(List.of("nonexistent-resident")));
        assertEquals(
                Optional.empty(),
                residencyRepository.findCurrentResidencyDetailsByResidentId("nonexistent-resident")
        );
        assertEquals(
                List.of(),
                residencyRepository.findResidencyHistoryByResidentId("nonexistent-resident")
        );
        assertEquals(
                List.of(),
                residencyRepository.findCurrentHousemates("nonexistent-apartment", "nonexistent-resident")
        );
        assertEquals(
                Optional.empty(),
                userRepository.findByResidentIdAndDeletedFalse("nonexistent-resident")
        );
        assertEquals(
                Optional.empty(),
                residentRepository.findByIdAndDeletedFalse("nonexistent-resident")
        );
        InvoiceRepository.ApartmentDebtDetailProjection debt = invoiceRepository.findDebtSummaryByApartmentId(
                "nonexistent-apartment",
                InvoiceStatus.PENDING,
                LocalDate.now()
        );
        assertNotNull(debt);
        assertEquals(0L, debt.getTotalAmount());
        assertEquals(0L, debt.getPendingInvoiceCount());
        assertEquals(0L, debt.getOverdueInvoiceCount());
        assertEquals(0L, debt.getOverdueAmount());
    }
}
