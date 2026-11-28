package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.common.enums.VehicleType;
import org.example.emeraldmanagementsystem.dto.request.VehicleRevokeRequest;
import org.example.emeraldmanagementsystem.dto.response.ApartmentVehiclesResponse;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Vehicle;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleRevokeServiceTest {
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
    void revokesVehicleAndDefaultsDateToToday() {
        Vehicle vehicle = vehicle(VehicleStatus.ACTIVE);
        Resident resident = new Resident();
        resident.setId("resident-1");
        resident.setFullName("Nguyễn Văn An");
        when(vehicleRepository.findByIdAndDeletedFalse("vehicle-1")).thenReturn(Optional.of(vehicle));
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(resident));

        ApartmentVehiclesResponse.VehicleItem response = apartmentService.revokeVehicle("vehicle-1", null);

        assertEquals(VehicleStatus.REVOKED, vehicle.getStatus());
        assertEquals(LocalDate.now(), vehicle.getRevokedAt());
        assertEquals(new ApartmentVehiclesResponse.VehicleItem(
                "vehicle-1",
                VehicleType.MOTORBIKE,
                "29B1-123.45",
                "MB-000123",
                new ApartmentVehiclesResponse.ResidentUser("resident-1", "Nguyễn Văn An"),
                LocalDate.of(2026, 11, 1),
                LocalDate.now(),
                VehicleStatus.REVOKED
        ), response);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    @Test
    void usesProvidedValidRevokeDate() {
        Vehicle vehicle = vehicle(VehicleStatus.ACTIVE);
        LocalDate revokedAt = LocalDate.of(2026, 11, 20);
        when(vehicleRepository.findByIdAndDeletedFalse("vehicle-1")).thenReturn(Optional.of(vehicle));
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.empty());

        ApartmentVehiclesResponse.VehicleItem response = apartmentService.revokeVehicle(
                "vehicle-1",
                new VehicleRevokeRequest(revokedAt)
        );

        assertEquals(revokedAt, response.revokedAt());
        assertNull(response.user());
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    @Test
    void returnsNotFoundForMissingVehicle() {
        when(vehicleRepository.findByIdAndDeletedFalse("missing-vehicle")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.revokeVehicle("missing-vehicle", null)
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("VEHICLE_NOT_FOUND", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void rejectsVehicleThatIsAlreadyRevoked() {
        when(vehicleRepository.findByIdAndDeletedFalse("vehicle-1"))
                .thenReturn(Optional.of(vehicle(VehicleStatus.REVOKED)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.revokeVehicle("vehicle-1", null)
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("VEHICLE_ALREADY_REVOKED", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void rejectsRevokeDateBeforeIssueDate() {
        when(vehicleRepository.findByIdAndDeletedFalse("vehicle-1"))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.revokeVehicle(
                        "vehicle-1",
                        new VehicleRevokeRequest(LocalDate.of(2026, 10, 31))
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_REVOKED_DATE", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    @Test
    void rejectsFutureRevokeDate() {
        when(vehicleRepository.findByIdAndDeletedFalse("vehicle-1"))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> apartmentService.revokeVehicle(
                        "vehicle-1",
                        new VehicleRevokeRequest(LocalDate.now().plusDays(1))
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_REVOKED_DATE", exception.getMessage());
        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    private Vehicle vehicle(VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId("vehicle-1");
        vehicle.setApartmentId("apartment-1");
        vehicle.setUserResidentId("resident-1");
        vehicle.setType(VehicleType.MOTORBIKE);
        vehicle.setPlateNumber("29B1-123.45");
        vehicle.setCardNumber("MB-000123");
        vehicle.setIssuedAt(LocalDate.of(2026, 11, 1));
        vehicle.setStatus(status);
        return vehicle;
    }
}
