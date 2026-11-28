package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.core.Response;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.ResidencyEndReason;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.common.enums.VehicleMoveOutAction;
import org.example.emeraldmanagementsystem.common.enums.VehicleStatus;
import org.example.emeraldmanagementsystem.dto.request.ResidencyMoveOutRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidencyMoveOutResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.entity.Vehicle;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidencyMoveOutServiceTest {
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Keycloak keycloak;
    @InjectMocks
    private ResidentServiceImplement residentService;

    @BeforeEach
    void setRealm() {
        ReflectionTestUtils.setField(residentService, "realm", "emerald");
    }

    @Test
    void movesOutMemberAndProcessesTransferAndRevokeActions() {
        Residency leavingResidency = residency("residency-member", "resident-member", ResidencyRole.MEMBER);
        Residency currentHead = residency("residency-head", "resident-head", ResidencyRole.HEAD);
        Resident leavingResident = resident("resident-member", "Resident Member");
        Apartment apartment = apartment();
        Vehicle transferVehicle = vehicle("vehicle-1");
        Vehicle revokeVehicle = vehicle("vehicle-2");
        when(residencyRepository.findByIdAndDeletedFalse("residency-member"))
                .thenReturn(Optional.of(leavingResidency));
        when(residencyRepository.findByApartmentIdAndEndDateIsNullAndDeletedFalse("apartment-1"))
                .thenReturn(List.of(leavingResidency, currentHead));
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        when(residentRepository.findByIdAndDeletedFalse("resident-member")).thenReturn(Optional.of(leavingResident));
        when(residentRepository.findByIdAndDeletedFalse("resident-head"))
                .thenReturn(Optional.of(resident("resident-head", "Resident Head")));
        when(vehicleRepository.findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
                "apartment-1",
                "resident-member",
                VehicleStatus.ACTIVE
        )).thenReturn(List.of(transferVehicle, revokeVehicle));

        ResidencyMoveOutResponse response = residentService.moveOutResidency(
                "residency-member",
                new ResidencyMoveOutRequest(
                        LocalDate.of(2026, 11, 30),
                        ResidencyEndReason.MOVED_OUT,
                        null,
                        List.of(
                                new ResidencyMoveOutRequest.VehicleAction(
                                        "vehicle-1",
                                        VehicleMoveOutAction.TRANSFER,
                                        "resident-head"
                                ),
                                new ResidencyMoveOutRequest.VehicleAction(
                                        "vehicle-2",
                                        VehicleMoveOutAction.REVOKE,
                                        null
                                )
                        )
                )
        );

        assertEquals(LocalDate.of(2026, 11, 30), leavingResidency.getEndDate());
        assertEquals(ResidencyEndReason.MOVED_OUT, leavingResidency.getEndReason());
        assertEquals("resident-head", transferVehicle.getUserResidentId());
        assertEquals(VehicleStatus.REVOKED, revokeVehicle.getStatus());
        assertEquals(LocalDate.of(2026, 11, 30), revokeVehicle.getRevokedAt());
        assertEquals(ApartmentStatus.OCCUPIED, apartment.getStatus());
        assertFalse(response.oldHeadAccountLocked());
        assertNull(response.newHead());
        assertEquals(2, response.vehicleResults().size());
        assertEquals("resident-head", response.vehicleResults().getFirst().transferToResidentId());
        assertNull(response.vehicleResults().getLast().transferToResidentId());
        verify(vehicleRepository).saveAllAndFlush(List.of(transferVehicle, revokeVehicle));
    }

    @Test
    void promotesNewHeadAndCreatesAccountBeforeLockingOldHead() {
        Residency leavingResidency = residency("residency-old-head", "resident-old-head", ResidencyRole.HEAD);
        Residency memberResidency = residency("residency-member", "resident-member", ResidencyRole.MEMBER);
        Resident leavingResident = resident("resident-old-head", "Old Head");
        Resident newHeadResident = resident("resident-member", "New Head");
        Apartment apartment = apartment();
        User oldHeadAccount = user("user-old", "0900000001", "resident-old-head");
        UserRepresentation newKeycloakUser = new UserRepresentation();
        newKeycloakUser.setId("keycloak-new-head");
        newKeycloakUser.setUsername("0912345678");
        UserRepresentation oldKeycloakUser = new UserRepresentation();
        oldKeycloakUser.setId("keycloak-old-head");
        oldKeycloakUser.setUsername("0900000001");
        UserRepresentation oldKeycloakAccount = new UserRepresentation();
        oldKeycloakAccount.setEnabled(true);
        Response createResponse = org.mockito.Mockito.mock(Response.class);

        when(residencyRepository.findByIdAndDeletedFalse("residency-old-head"))
                .thenReturn(Optional.of(leavingResidency));
        when(residencyRepository.findByApartmentIdAndEndDateIsNullAndDeletedFalse("apartment-1"))
                .thenReturn(List.of(leavingResidency, memberResidency));
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        when(residentRepository.findByIdAndDeletedFalse("resident-old-head")).thenReturn(Optional.of(leavingResident));
        when(residentRepository.findByIdAndDeletedFalse("resident-member")).thenReturn(Optional.of(newHeadResident));
        when(userRepository.existsByUsernameAndDeletedFalse("0912345678")).thenReturn(false);
        when(userRepository.findByResidentIdAndDeletedFalse("resident-member")).thenReturn(Optional.empty());
        when(userRepository.findByResidentIdAndDeletedFalse("resident-old-head"))
                .thenReturn(Optional.of(oldHeadAccount));
        when(vehicleRepository.findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
                "apartment-1",
                "resident-old-head",
                VehicleStatus.ACTIVE
        )).thenReturn(List.of());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            if (savedUser.getId() == null) {
                savedUser.setId("user-new-head");
            }
            return savedUser;
        });
        when(keycloak.realm("emerald").users().create(any(UserRepresentation.class))).thenReturn(createResponse);
        when(createResponse.getStatus()).thenReturn(201);
        when(createResponse.getLocation()).thenReturn(URI.create(
                "http://keycloak/admin/realms/emerald/users/keycloak-new-head"
        ));
        when(keycloak.realm("emerald").users().search("0900000001", true))
                .thenReturn(List.of(oldKeycloakUser));
        when(keycloak.realm("emerald").users().get("keycloak-old-head").toRepresentation())
                .thenReturn(oldKeycloakAccount);
        when(keycloak.realm("emerald").roles().get("RESIDENT").toRepresentation())
                .thenReturn(new RoleRepresentation());

        ResidencyMoveOutResponse response = residentService.moveOutResidency(
                "residency-old-head",
                new ResidencyMoveOutRequest(
                        LocalDate.of(2026, 11, 30),
                        ResidencyEndReason.MOVED_OUT,
                        new ResidencyMoveOutRequest.NewHead("resident-member", "0912345678", "Temp@12345"),
                        List.of()
                )
        );

        assertEquals(ResidencyRole.HEAD, memberResidency.getRole());
        assertNull(memberResidency.getRelation());
        assertEquals("LOCKED", oldHeadAccount.getStatus());
        assertEquals(ApartmentStatus.OCCUPIED, apartment.getStatus());
        assertTrue(response.oldHeadAccountLocked());
        assertEquals(new ResidencyMoveOutResponse.NewHead(
                "resident-member",
                "residency-member",
                true,
                "0912345678"
        ), response.newHead());
        assertFalse(oldKeycloakAccount.isEnabled());

        ArgumentCaptor<UserRepresentation> keycloakUserCaptor =
                ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloak.realm("emerald").users()).create(keycloakUserCaptor.capture());
        assertEquals("0912345678", keycloakUserCaptor.getValue().getUsername());
        assertEquals("Temp@12345", keycloakUserCaptor.getValue().getCredentials().getFirst().getValue());
        verify(keycloak.realm("emerald").users().get("keycloak-old-head"))
                .update(oldKeycloakAccount);
        InOrder residencySaveOrder = inOrder(residencyRepository);
        residencySaveOrder.verify(residencyRepository).saveAndFlush(leavingResidency);
        residencySaveOrder.verify(residencyRepository).saveAndFlush(memberResidency);
    }

    @Test
    void requiresAnActionForEveryActiveVehicleAndIncludesMissingIds() {
        Residency leavingResidency = residency("residency-member", "resident-member", ResidencyRole.MEMBER);
        when(residencyRepository.findByIdAndDeletedFalse("residency-member"))
                .thenReturn(Optional.of(leavingResidency));
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment()));
        when(residentRepository.findByIdAndDeletedFalse("resident-member"))
                .thenReturn(Optional.of(resident("resident-member", "Resident Member")));
        when(residencyRepository.findByApartmentIdAndEndDateIsNullAndDeletedFalse("apartment-1"))
                .thenReturn(List.of(leavingResidency, residency("residency-head", "resident-head", ResidencyRole.HEAD)));
        when(vehicleRepository.findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
                "apartment-1",
                "resident-member",
                VehicleStatus.ACTIVE
        )).thenReturn(List.of(vehicle("vehicle-missing")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.moveOutResidency(
                        "residency-member",
                        new ResidencyMoveOutRequest(
                                LocalDate.of(2026, 11, 30),
                                ResidencyEndReason.MOVED_OUT,
                                null,
                                List.of()
                        )
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("VEHICLE_ACTION_REQUIRED"));
        assertTrue(exception.getMessage().contains("vehicle-missing"));
        assertNull(leavingResidency.getEndDate());
        verify(residencyRepository, never()).saveAndFlush(any(Residency.class));
        verify(apartmentRepository, never()).saveAndFlush(any(Apartment.class));
    }

    @Test
    void marksApartmentVacantWhenLastResidentMovesOut() {
        Residency leavingResidency = residency("residency-last", "resident-last", ResidencyRole.MEMBER);
        Resident leavingResident = resident("resident-last", "Last Resident");
        Apartment apartment = apartment();
        when(residencyRepository.findByIdAndDeletedFalse("residency-last"))
                .thenReturn(Optional.of(leavingResidency));
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        when(residentRepository.findByIdAndDeletedFalse("resident-last")).thenReturn(Optional.of(leavingResident));
        when(residencyRepository.findByApartmentIdAndEndDateIsNullAndDeletedFalse("apartment-1"))
                .thenReturn(List.of(leavingResidency));
        when(vehicleRepository.findByApartmentIdAndUserResidentIdAndStatusAndDeletedFalse(
                "apartment-1",
                "resident-last",
                VehicleStatus.ACTIVE
        )).thenReturn(List.of());

        ResidencyMoveOutResponse response = residentService.moveOutResidency(
                "residency-last",
                new ResidencyMoveOutRequest(
                        LocalDate.of(2026, 11, 30),
                        ResidencyEndReason.MOVED_OUT,
                        null,
                        List.of()
                )
        );

        assertEquals(ApartmentStatus.VACANT, apartment.getStatus());
        assertEquals(ApartmentStatus.VACANT, response.apartment().status());
        assertFalse(response.oldHeadAccountLocked());
        assertNull(response.newHead());
    }

    @Test
    void requiresNewHeadWhenDepartingHeadLeavesOtherResidents() {
        Residency headResidency = residency("residency-head", "resident-head", ResidencyRole.HEAD);
        when(residencyRepository.findByIdAndDeletedFalse("residency-head")).thenReturn(Optional.of(headResidency));
        when(residencyRepository.findByApartmentIdAndEndDateIsNullAndDeletedFalse("apartment-1"))
                .thenReturn(List.of(
                        headResidency,
                        residency("residency-member", "resident-member", ResidencyRole.MEMBER)
                ));
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment()));
        when(residentRepository.findByIdAndDeletedFalse("resident-head"))
                .thenReturn(Optional.of(resident("resident-head", "Head")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.moveOutResidency(
                        "residency-head",
                        new ResidencyMoveOutRequest(
                                LocalDate.of(2026, 11, 30),
                                ResidencyEndReason.MOVED_OUT,
                                null,
                                List.of()
                        )
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("NEW_HEAD_REQUIRED", exception.getMessage());
        verify(residencyRepository, never()).saveAndFlush(any(Residency.class));
    }

    @Test
    void rejectsEndDateBeforeResidencyStartDate() {
        Residency residency = residency("residency-member", "resident-member", ResidencyRole.MEMBER);
        when(residencyRepository.findByIdAndDeletedFalse("residency-member")).thenReturn(Optional.of(residency));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.moveOutResidency(
                        "residency-member",
                        new ResidencyMoveOutRequest(
                                LocalDate.of(2024, 2, 29),
                                ResidencyEndReason.MOVED_OUT,
                                null,
                                List.of()
                        )
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_END_DATE", exception.getMessage());
        verify(apartmentRepository, never()).findByIdAndDeletedFalse("apartment-1");
    }

    private Residency residency(String id, String residentId, ResidencyRole role) {
        Residency residency = new Residency();
        residency.setId(id);
        residency.setApartmentId("apartment-1");
        residency.setResidentId(residentId);
        residency.setRole(role);
        residency.setStartDate(LocalDate.of(2024, 3, 1));
        return residency;
    }

    private Resident resident(String id, String fullName) {
        Resident resident = new Resident();
        resident.setId(id);
        resident.setFullName(fullName);
        resident.setEmail("resident@example.com");
        return resident;
    }

    private Apartment apartment() {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setCode("A-1205");
        apartment.setStatus(ApartmentStatus.OCCUPIED);
        return apartment;
    }

    private Vehicle vehicle(String id) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setApartmentId("apartment-1");
        vehicle.setUserResidentId("resident-member");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return vehicle;
    }

    private User user(String id, String username, String residentId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setResidentId(residentId);
        user.setStatus("ACTIVE");
        return user;
    }
}
