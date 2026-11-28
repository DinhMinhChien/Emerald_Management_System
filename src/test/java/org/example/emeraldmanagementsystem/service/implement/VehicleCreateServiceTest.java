package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.request.VehicleCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Resident;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleCreateServiceTest {
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private ResidentRepository residentRepository;
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
    void normalizesPlateAndDefaultsIssuedAt() {
        Resident resident = resident();
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(new Apartment()));
        when(residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                "resident-1"
        )).thenReturn(true);
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident));
        when(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("29B1-123.45")).thenReturn(false);
        when(vehicleRepository.existsByActiveCardNumberAndDeletedFalse("MB-000123")).thenReturn(false);
        when(vehicleQuotaRepository.findByVehicleTypeAndDeletedFalse(VehicleType.MOTORBIKE))
                .thenReturn(Optional.of(quota(3)));
        when(vehicleRepository.countByApartmentIdAndTypeAndStatusAndDeletedFalse(
                "apartment-1",
                VehicleType.MOTORBIKE,
                VehicleStatus.ACTIVE
        )).thenReturn(1L);
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setId("vehicle-1");
            return vehicle;
        });

        ApartmentVehiclesResponse.VehicleItem response = apartmentService.createVehicle(
                "apartment-1",
                new VehicleCreateRequest(
                        VehicleType.MOTORBIKE,
                        " 29b1-123.45 ",
                        "MB-000123",
                        "resident-1",
                        null
                )
        );

        assertEquals(new ApartmentVehiclesResponse.VehicleItem(
                "vehicle-1",
                VehicleType.MOTORBIKE,
                "29B1-123.45",
                "MB-000123",
                new ApartmentVehiclesResponse.ResidentUser("resident-1", "Nguyễn Văn An"),
                LocalDate.now(),
                null,
                VehicleStatus.ACTIVE
        ), response);
        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).saveAndFlush(vehicleCaptor.capture());
        assertEquals("29B1-123.45", vehicleCaptor.getValue().getPlateNumber());
        assertEquals(VehicleStatus.ACTIVE, vehicleCaptor.getValue().getStatus());
    }

    @Test
    void rejectsFutureIssuedDate() {
        mockValidResidentAndApartment();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle(
                        "apartment-1",
                        request(LocalDate.now().plusDays(1))
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("ISSUED_AT_IN_FUTURE", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void rejectsResidentWhoDoesNotCurrentlyLiveInApartment() {
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(new Apartment()));
        when(residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                "resident-1"
        )).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle("apartment-1", request(null))
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("USER_NOT_IN_APARTMENT", exception.getMessage());
    }

    @Test
    void rejectsDuplicateActivePlateNumber() {
        mockValidResidentAndApartment();
        when(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("29B1-123.45")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle("apartment-1", request(null))
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("PLATE_NUMBER_DUPLICATED", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void rejectsDuplicateActiveCardNumber() {
        mockValidResidentAndApartment();
        when(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("29B1-123.45")).thenReturn(false);
        when(vehicleRepository.existsByActiveCardNumberAndDeletedFalse("MB-000123")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle("apartment-1", request(null))
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("CARD_NUMBER_DUPLICATED", exception.getMessage());
    }

    @Test
    void returnsQuotaDetailsWhenApartmentHasReachedVehicleLimit() {
        mockValidResidentAndApartment();
        when(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("29B1-123.45")).thenReturn(false);
        when(vehicleRepository.existsByActiveCardNumberAndDeletedFalse("MB-000123")).thenReturn(false);
        when(vehicleQuotaRepository.findByVehicleTypeAndDeletedFalse(VehicleType.MOTORBIKE))
                .thenReturn(Optional.of(quota(2)));
        when(vehicleRepository.countByApartmentIdAndTypeAndStatusAndDeletedFalse(
                "apartment-1",
                VehicleType.MOTORBIKE,
                VehicleStatus.ACTIVE
        )).thenReturn(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle("apartment-1", request(null))
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("VEHICLE_QUOTA_EXCEEDED\ntype=MOTORBIKE\nmax=2\nused=2", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void mapsConcurrentPlateUniquenessViolationToConflict() {
        mockValidResidentAndApartment();
        when(vehicleRepository.existsByActivePlateNumberAndDeletedFalse("29B1-123.45")).thenReturn(false);
        when(vehicleRepository.existsByActiveCardNumberAndDeletedFalse("MB-000123")).thenReturn(false);
        when(vehicleQuotaRepository.findByVehicleTypeAndDeletedFalse(VehicleType.MOTORBIKE))
                .thenReturn(Optional.of(quota(3)));
        when(vehicleRepository.countByApartmentIdAndTypeAndStatusAndDeletedFalse(
                "apartment-1",
                VehicleType.MOTORBIKE,
                VehicleStatus.ACTIVE
        )).thenReturn(0L);
        when(vehicleRepository.saveAndFlush(any(Vehicle.class))).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate",
                        new SQLIntegrityConstraintViolationException(
                                "Duplicate entry for key 'vehicles.active_plate_number'",
                                "23000",
                                1062
                        )
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.createVehicle("apartment-1", request(null))
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("PLATE_NUMBER_DUPLICATED", exception.getMessage());
    }

    private void mockValidResidentAndApartment() {
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(new Apartment()));
        when(residencyRepository.existsByApartmentIdAndResidentIdAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                "resident-1"
        )).thenReturn(true);
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident()));
    }

    private VehicleCreateRequest request(LocalDate issuedAt) {
        return new VehicleCreateRequest(
                VehicleType.MOTORBIKE,
                "29B1-123.45",
                "MB-000123",
                "resident-1",
                issuedAt
        );
    }

    private Resident resident() {
        Resident resident = new Resident();
        resident.setId("resident-1");
        resident.setFullName("Nguyễn Văn An");
        return resident;
    }

    private VehicleQuota quota(int maxPerApartment) {
        VehicleQuota quota = new VehicleQuota();
        quota.setVehicleType(VehicleType.MOTORBIKE);
        quota.setMaxPerApartment(maxPerApartment);
        return quota;
    }
}
