package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRelation;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.request.ApartmentCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentDetailResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentListResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentResidenciesResponse;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.FeeTariff;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.entity.VehicleQuota;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.FeeTariffRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleQuotaRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApartmentServiceImplementTest {
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private TowerRepository towerRepository;
    @Mock
    private FeeTariffRepository feeTariffRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private VehicleQuotaRepository vehicleQuotaRepository;
    @InjectMocks
    private ApartmentServiceImplement apartmentService;

    @Test
    void filtersPagesAndEnrichesApartmentsWithHeadAndDebt() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setTowerId("tower-1");
        apartment.setCode("A-1205");
        apartment.setFloor(12);
        apartment.setAreaM2(new BigDecimal("72.5"));
        apartment.setStatus(ApartmentStatus.OCCUPIED);
        when(apartmentRepository.searchApartments(
                eq("%an%"),
                eq("tower-1"),
                eq(ApartmentStatus.OCCUPIED),
                eq(true),
                eq(ResidencyRole.HEAD),
                any()
        )).thenReturn(new PageImpl<>(List.of(apartment), PageRequest.of(1, 2), 5));

        ResidencyRepository.ActiveHeadProjection head = new ResidencyRepository.ActiveHeadProjection() {
            @Override
            public String getApartmentId() {
                return "apartment-1";
            }

            @Override
            public String getResidentId() {
                return "resident-1";
            }

            @Override
            public String getFullName() {
                return "Nguyễn Văn An";
            }

            @Override
            public String getPhone() {
                return "0901234567";
            }
        };
        when(residencyRepository.findActiveHeadDetailsByApartmentIds(List.of("apartment-1")))
                .thenReturn(List.of(head));

        InvoiceRepository.ApartmentDebtProjection debt = new InvoiceRepository.ApartmentDebtProjection() {
            @Override
            public String getApartmentId() {
                return "apartment-1";
            }

            @Override
            public Long getDebtAmount() {
                return 3450000L;
            }

            @Override
            public Long getOverdueInvoiceCount() {
                return 2L;
            }
        };
        when(invoiceRepository.findApartmentDebtSummaries(
                eq(List.of("apartment-1")),
                eq(InvoiceStatus.PENDING),
                any(LocalDate.class)
        )).thenReturn(List.of(debt));

        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        when(towerRepository.findAllById(anyIterable())).thenReturn(List.of(tower));

        ApartmentListResponse response = apartmentService.getApartments(
                " an ",
                "tower-1",
                ApartmentStatus.OCCUPIED,
                true,
                1,
                2
        );

        assertEquals(5, response.total());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        ApartmentListResponse.ApartmentItem item = response.items().getFirst();
        assertEquals("A-1205", item.code());
        assertEquals(new ApartmentListResponse.Tower("tower-1", "A", "Tòa A"), item.tower());
        assertEquals(
                new ApartmentListResponse.Head("resident-1", "Nguyễn Văn An", "0901234567"),
                item.head()
        );
        assertEquals(3450000L, item.debtAmount());
        assertEquals(2L, item.overdueInvoiceCount());
        verify(apartmentRepository).searchApartments(
                eq("%an%"),
                eq("tower-1"),
                eq(ApartmentStatus.OCCUPIED),
                eq(true),
                eq(ResidencyRole.HEAD),
                any()
        );
    }

    @Test
    void rejectsInvalidPagination() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.getApartments(null, null, null, null, 0, 101)
        );

        assertEquals(400, exception.getErrorCode());
    }

    @Test
    void createsVacantApartmentAndReturnsDetailShape() {
        ApartmentCreateRequest request = new ApartmentCreateRequest(
                "tower-1",
                "A-1205",
                12,
                new BigDecimal("72.5"),
                2,
                2
        );
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));
        when(apartmentRepository.existsByCode("A-1205")).thenReturn(false);
        when(apartmentRepository.saveAndFlush(any(Apartment.class))).thenAnswer(invocation -> {
            Apartment apartment = invocation.getArgument(0);
            apartment.setId("apartment-1");
            return apartment;
        });
        FeeTariff tariff = new FeeTariff();
        tariff.setFeeType(FeeType.SERVICE);
        tariff.setTierName("Mức 2");
        tariff.setMinAreaM2(new BigDecimal("70"));
        tariff.setMaxAreaM2(new BigDecimal("80"));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.SERVICE), any(LocalDate.class)))
                .thenReturn(List.of(tariff));

        ApartmentDetailResponse response = apartmentService.createApartment(request);

        assertEquals("apartment-1", response.id());
        assertEquals("A-1205", response.code());
        assertEquals(new ApartmentDetailResponse.Tower("tower-1", "A", "Tòa A"), response.tower());
        assertEquals(ApartmentStatus.VACANT, response.status());
        assertEquals("Mức 2", response.serviceTierName());
        assertNull(response.head());
        assertEquals(0, response.occupantCount());
        assertEquals(new ApartmentDetailResponse.Debt(0, 0, 0, 0), response.debt());
        assertEquals(List.of(), response.residencyHistory());
    }

    @Test
    void rejectsApartmentCreationWhenTowerDoesNotExist() {
        ApartmentCreateRequest request = new ApartmentCreateRequest(
                "missing-tower",
                "A-1205",
                12,
                new BigDecimal("72.5"),
                2,
                2
        );
        when(towerRepository.findByIdAndDeletedFalse("missing-tower")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createApartment(request)
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("TOWER_NOT_FOUND", exception.getMessage());
        verify(apartmentRepository, never()).saveAndFlush(any(Apartment.class));
    }

    @Test
    void rejectsDuplicateApartmentCode() {
        ApartmentCreateRequest request = new ApartmentCreateRequest(
                "tower-1",
                "A-1205",
                12,
                new BigDecimal("72.5"),
                2,
                2
        );
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(new Tower()));
        when(apartmentRepository.existsByCode("A-1205")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createApartment(request)
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("APARTMENT_CODE_DUPLICATED", exception.getMessage());
        verify(apartmentRepository, never()).saveAndFlush(any(Apartment.class));
    }

    @Test
    void mapsDatabaseDuplicateKeyToConflict() {
        ApartmentCreateRequest request = new ApartmentCreateRequest(
                "tower-1",
                "A-1205",
                12,
                new BigDecimal("72.5"),
                2,
                2
        );
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(new Tower()));
        when(apartmentRepository.existsByCode("A-1205")).thenReturn(false);
        when(apartmentRepository.saveAndFlush(any(Apartment.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new SQLException("duplicate key", "23000", 1062)
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createApartment(request)
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("APARTMENT_CODE_DUPLICATED", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveApartmentArea() {
        ApartmentCreateRequest request = new ApartmentCreateRequest(
                "tower-1",
                "A-1205",
                12,
                BigDecimal.ZERO,
                2,
                2
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createApartment(request)
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
        verify(towerRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void returnsApartmentDetailsWithCurrentHeadResidentsDebtAndTier() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setTowerId("tower-1");
        apartment.setCode("A-1205");
        apartment.setFloor(12);
        apartment.setAreaM2(new BigDecimal("72.5"));
        apartment.setBedrooms(2);
        apartment.setBathrooms(2);
        apartment.setStatus(ApartmentStatus.OCCUPIED);
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));

        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));

        ResidencyRepository.ResidencyDetailProjection head = residency(
                "residency-head", "resident-1", "Nguyễn Văn An", "0901234567",
                LocalDate.of(1985, 1, 1), Gender.MALE,
                ResidencyRole.HEAD, null, LocalDate.of(2024, 3, 1), null, null
        );
        ResidencyRepository.ResidencyDetailProjection member = residency(
                "residency-member", "resident-2", "Nguyễn Thị Hoa", null,
                LocalDate.of(1990, 5, 12), Gender.FEMALE,
                ResidencyRole.MEMBER, ResidencyRelation.SPOUSE, LocalDate.of(2024, 3, 2), null, null
        );
        ResidencyRepository.ResidencyDetailProjection formerMember = residency(
                "residency-former", "resident-3", "Trần Văn Bình", null,
                LocalDate.of(2010, 4, 1), Gender.MALE,
                ResidencyRole.MEMBER, ResidencyRelation.CHILD, LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 2, 1), ResidencyEndReason.MOVED_OUT
        );
        when(residencyRepository.findResidencyDetailsByApartmentId("apartment-1", null))
                .thenReturn(List.of(head, member, formerMember));

        InvoiceRepository.ApartmentDebtDetailProjection debt = new InvoiceRepository.ApartmentDebtDetailProjection() {
            @Override
            public Long getTotalAmount() {
                return 3450000L;
            }

            @Override
            public Long getPendingInvoiceCount() {
                return 2L;
            }

            @Override
            public Long getOverdueInvoiceCount() {
                return 1L;
            }

            @Override
            public Long getOverdueAmount() {
                return 1500000L;
            }
        };
        when(invoiceRepository.findDebtSummaryByApartmentId(
                eq("apartment-1"),
                eq(InvoiceStatus.PENDING),
                any(LocalDate.class)
        )).thenReturn(debt);
        FeeTariff tariff = new FeeTariff();
        tariff.setTierName("Mức 2");
        tariff.setMinAreaM2(new BigDecimal("70"));
        tariff.setMaxAreaM2(new BigDecimal("80"));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.SERVICE), any(LocalDate.class)))
                .thenReturn(List.of(tariff));

        ApartmentDetailResponse response = apartmentService.getApartmentDetails("apartment-1");

        assertEquals("A-1205", response.code());
        assertEquals(new ApartmentDetailResponse.Tower("tower-1", "A", "Tòa A"), response.tower());
        assertEquals("Mức 2", response.serviceTierName());
        assertEquals(
                new ApartmentDetailResponse.Head(
                        "resident-1", "Nguyễn Văn An", "0901234567", LocalDate.of(2024, 3, 1)
                ),
                response.head()
        );
        assertEquals(2, response.occupantCount());
        assertEquals(new ApartmentDetailResponse.Debt(3450000, 2, 1, 1500000), response.debt());
        assertEquals(3, response.residencyHistory().size());
        assertEquals("residency-head", response.residencyHistory().getFirst().residencyId());
        assertEquals("residency-former", response.residencyHistory().getLast().residencyId());
    }

    @Test
    void returnsNotFoundWhenApartmentDoesNotExist() {
        when(apartmentRepository.findByIdAndDeletedFalse("missing-apartment")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.getApartmentDetails("missing-apartment")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("APARTMENT_NOT_FOUND", exception.getMessage());
        verify(towerRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void returnsApartmentResidenciesWithCurrentFilterAndAccountFlags() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        ResidencyRepository.ResidencyDetailProjection head = residency(
                "residency-head", "resident-1", "Nguyễn Văn An", "0901234567",
                LocalDate.of(1985, 1, 1), Gender.MALE,
                ResidencyRole.HEAD, null, LocalDate.of(2024, 3, 1), null, null
        );
        ResidencyRepository.ResidencyDetailProjection member = residency(
                "residency-member", "resident-2", "Nguyễn Thị Hoa", null,
                LocalDate.of(1990, 5, 12), Gender.FEMALE,
                ResidencyRole.MEMBER, ResidencyRelation.SPOUSE, LocalDate.of(2024, 3, 2), null, null
        );
        when(residencyRepository.findResidencyDetailsByApartmentId("apartment-1", true))
                .thenReturn(List.of(head, member));
        when(userRepository.findResidentIdsWithAccounts(List.of("resident-1", "resident-2")))
                .thenReturn(List.of("resident-1"));

        ApartmentResidenciesResponse response = apartmentService.getApartmentResidencies("apartment-1", true);

        assertEquals(2, response.items().size());
        ApartmentResidenciesResponse.ResidencyItem headItem = response.items().getFirst();
        assertEquals("residency-head", headItem.residencyId());
        assertEquals(LocalDate.of(1985, 1, 1), headItem.resident().birthDate());
        assertEquals(Gender.MALE, headItem.resident().gender());
        assertEquals(true, headItem.resident().hasAccount());
        ApartmentResidenciesResponse.ResidencyItem memberItem = response.items().getLast();
        assertEquals(ResidencyRelation.SPOUSE, memberItem.relation());
        assertEquals(false, memberItem.resident().hasAccount());
        verify(residencyRepository).findResidencyDetailsByApartmentId("apartment-1", true);
    }

    @Test
    void returnsNotFoundWhenListingResidenciesForMissingApartment() {
        when(apartmentRepository.findByIdAndDeletedFalse("missing-apartment")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.getApartmentResidencies("missing-apartment", null)
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("APARTMENT_NOT_FOUND", exception.getMessage());
        verify(residencyRepository, never()).findResidencyDetailsByApartmentId(any(), any());
    }

    @Test
    void returnsVehiclesQuotasAndMonthlyParkingFees() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        when(vehicleRepository.findVehicleItemsByApartmentId("apartment-1", null))
                .thenReturn(List.of(
                        vehicle("vehicle-2", VehicleType.CAR, "30A-123.45", "CAR-000123",
                                "resident-2", "Nguyễn Thị Hoa", LocalDate.of(2024, 3, 6),
                                LocalDate.of(2025, 1, 1), VehicleStatus.REVOKED),
                        vehicle("vehicle-1", VehicleType.MOTORBIKE, "29B1-123.45", "MB-000123",
                                "resident-1", "Nguyễn Văn An", LocalDate.of(2024, 3, 5), null,
                                VehicleStatus.ACTIVE)
                ));

        VehicleQuota motorbikeQuota = new VehicleQuota();
        motorbikeQuota.setVehicleType(VehicleType.MOTORBIKE);
        motorbikeQuota.setMaxPerApartment(3);
        VehicleQuota carQuota = new VehicleQuota();
        carQuota.setVehicleType(VehicleType.CAR);
        carQuota.setMaxPerApartment(1);
        when(vehicleQuotaRepository.findAllByDeletedFalseOrderByVehicleTypeAsc())
                .thenReturn(List.of(motorbikeQuota, carQuota));

        FeeTariff motorbikeTariff = new FeeTariff();
        motorbikeTariff.setAmount(100000L);
        FeeTariff carTariff = new FeeTariff();
        carTariff.setAmount(1200000L);
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.PARKING_MOTORBIKE), any(LocalDate.class)))
                .thenReturn(List.of(motorbikeTariff));
        when(feeTariffRepository.findCurrentByFeeType(eq(FeeType.PARKING_CAR), any(LocalDate.class)))
                .thenReturn(List.of(carTariff));

        ApartmentVehiclesResponse response = apartmentService.getApartmentVehicles("apartment-1", "ALL");

        assertEquals(2, response.items().size());
        assertEquals(VehicleStatus.REVOKED, response.items().getFirst().status());
        assertEquals(VehicleStatus.ACTIVE, response.items().getLast().status());
        assertEquals(3, response.quotas().getFirst().max());
        assertEquals(1, response.quotas().getFirst().used());
        assertEquals(0, response.quotas().getLast().used());
        assertEquals(new ApartmentVehiclesResponse.ParkingFeeItem(1, 100000L, 100000L),
                response.parkingFee().motorbike());
        assertEquals(new ApartmentVehiclesResponse.ParkingFeeItem(0, 1200000L, 0L),
                response.parkingFee().car());
        assertEquals(100000L, response.parkingFee().totalAmount());
        verify(vehicleRepository).findVehicleItemsByApartmentId("apartment-1", null);
    }

    @Test
    void rejectsUnsupportedVehicleStatus() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.getApartmentVehicles("apartment-1", "REVOKED")
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
        verify(vehicleRepository, never()).findVehicleItemsByApartmentId(any(), any());
    }

    private ResidencyRepository.ResidencyDetailProjection residency(
            String residencyId,
            String residentId,
            String fullName,
            String phone,
            LocalDate birthDate,
            Gender gender,
            ResidencyRole role,
            ResidencyRelation relation,
            LocalDate startDate,
            LocalDate endDate,
            ResidencyEndReason endReason
    ) {
        return new ResidencyRepository.ResidencyDetailProjection() {
            @Override
            public String getResidencyId() {
                return residencyId;
            }

            @Override
            public String getResidentId() {
                return residentId;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public LocalDate getBirthDate() {
                return birthDate;
            }

            @Override
            public Gender getGender() {
                return gender;
            }

            @Override
            public ResidencyRole getRole() {
                return role;
            }

            @Override
            public ResidencyRelation getRelation() {
                return relation;
            }

            @Override
            public LocalDate getStartDate() {
                return startDate;
            }

            @Override
            public LocalDate getEndDate() {
                return endDate;
            }

            @Override
            public ResidencyEndReason getEndReason() {
                return endReason;
            }
        };
    }

    private VehicleRepository.VehicleItemProjection vehicle(
            String id,
            VehicleType type,
            String plateNumber,
            String cardNumber,
            String residentId,
            String residentFullName,
            LocalDate issuedAt,
            LocalDate revokedAt,
            VehicleStatus status
    ) {
        return new VehicleRepository.VehicleItemProjection() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public VehicleType getType() {
                return type;
            }

            @Override
            public String getPlateNumber() {
                return plateNumber;
            }

            @Override
            public String getCardNumber() {
                return cardNumber;
            }

            @Override
            public String getResidentId() {
                return residentId;
            }

            @Override
            public String getResidentFullName() {
                return residentFullName;
            }

            @Override
            public LocalDate getIssuedAt() {
                return issuedAt;
            }

            @Override
            public LocalDate getRevokedAt() {
                return revokedAt;
            }

            @Override
            public VehicleStatus getStatus() {
                return status;
            }
        };
    }
}
