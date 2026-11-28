package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.core.Response;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.Gender;
import org.example.emeraldmanagementsystem.common.enums.ResidencyRole;
import org.example.emeraldmanagementsystem.dto.request.ResidentHeadCreateRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentHeadCreateResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.Resident;
import org.example.emeraldmanagementsystem.entity.Residency;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.ResidentRepository;
import org.example.emeraldmanagementsystem.repository.ResidencyRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentHeadCreationServiceTest {
    @Mock
    private ResidentRepository residentRepository;
    @Mock
    private ResidencyRepository residencyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Keycloak keycloak;
    @InjectMocks
    private ResidentServiceImplement residentService;

    @BeforeEach
    void setRealm() {
        ReflectionTestUtils.setField(residentService, "realm", "emerald");
    }

    @Test
    void createsResidentHeadAndTemporaryKeycloakAccount() {
        ResidentHeadCreateRequest request = request();
        Apartment apartment = apartment(ApartmentStatus.VACANT);
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1")).thenReturn(Optional.of(apartment));
        when(residencyRepository.existsByApartmentIdAndRoleAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                ResidencyRole.HEAD
        )).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedFalse("0901234567")).thenReturn(false);
        when(residentRepository.saveAndFlush(any(Resident.class))).thenAnswer(invocation -> {
            Resident resident = invocation.getArgument(0);
            resident.setId("resident-1");
            return resident;
        });
        when(residencyRepository.saveAndFlush(any(Residency.class))).thenAnswer(invocation -> {
            Residency residency = invocation.getArgument(0);
            residency.setId("residency-1");
            return residency;
        });
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(apartmentRepository.saveAndFlush(apartment)).thenReturn(apartment);

        Response keycloakResponse = org.mockito.Mockito.mock(Response.class);
        when(keycloak.realm("emerald").users().create(any(UserRepresentation.class))).thenReturn(keycloakResponse);
        when(keycloakResponse.getStatus()).thenReturn(201);
        when(keycloakResponse.getLocation()).thenReturn(URI.create(
                "http://keycloak/admin/realms/emerald/users/keycloak-user-1"
        ));
        RoleRepresentation residentRole = new RoleRepresentation();
        when(keycloak.realm("emerald").roles().get("RESIDENT").toRepresentation()).thenReturn(residentRole);

        ResidentHeadCreateResponse response = residentService.createResidentHead(request);

        assertEquals(new ResidentHeadCreateResponse(
                "resident-1",
                "residency-1",
                "user-1",
                "0901234567",
                "apartment-1",
                ApartmentStatus.OCCUPIED
        ), response);

        ArgumentCaptor<UserRepresentation> keycloakUserCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloak.realm("emerald").users()).create(keycloakUserCaptor.capture());
        UserRepresentation keycloakUser = keycloakUserCaptor.getValue();
        assertEquals("0901234567", keycloakUser.getUsername());
        assertEquals("Nguyễn Văn An", keycloakUser.getFirstName());
        assertEquals(true, keycloakUser.isEnabled());
        assertEquals("Tam@12345", keycloakUser.getCredentials().getFirst().getValue());
        assertEquals(true, keycloakUser.getCredentials().getFirst().isTemporary());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsApartmentUnderRepair() {
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(apartment(ApartmentStatus.REPAIRING)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.createResidentHead(request())
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("APARTMENT_REPAIRING", exception.getMessage());
    }

    @Test
    void mapsKeycloakPasswordPolicyFailureToWeakPassword() {
        ResidentHeadCreateRequest request = request();
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(apartment(ApartmentStatus.VACANT)));
        when(residencyRepository.existsByApartmentIdAndRoleAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                ResidencyRole.HEAD
        )).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedFalse("0901234567")).thenReturn(false);
        when(residentRepository.saveAndFlush(any(Resident.class))).thenAnswer(invocation -> {
            Resident resident = invocation.getArgument(0);
            resident.setId("resident-1");
            return resident;
        });
        when(residencyRepository.saveAndFlush(any(Residency.class))).thenAnswer(invocation -> {
            Residency residency = invocation.getArgument(0);
            residency.setId("residency-1");
            return residency;
        });
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(apartmentRepository.saveAndFlush(any(Apartment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Response keycloakResponse = org.mockito.Mockito.mock(Response.class);
        when(keycloak.realm("emerald").users().create(any(UserRepresentation.class))).thenReturn(keycloakResponse);
        when(keycloakResponse.getStatus()).thenReturn(400);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.createResidentHead(request)
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("WEAK_PASSWORD", exception.getMessage());
    }

    @Test
    void cleansUpKeycloakUserWhenCreateResponseHasNoLocation() {
        ResidentHeadCreateRequest request = request();
        when(apartmentRepository.findByIdAndDeletedFalse("apartment-1"))
                .thenReturn(Optional.of(apartment(ApartmentStatus.VACANT)));
        when(residencyRepository.existsByApartmentIdAndRoleAndEndDateIsNullAndDeletedFalse(
                "apartment-1",
                ResidencyRole.HEAD
        )).thenReturn(false);
        when(userRepository.existsByUsernameAndDeletedFalse("0901234567")).thenReturn(false);
        when(residentRepository.saveAndFlush(any(Resident.class))).thenAnswer(invocation -> {
            Resident resident = invocation.getArgument(0);
            resident.setId("resident-1");
            return resident;
        });
        when(residencyRepository.saveAndFlush(any(Residency.class))).thenAnswer(invocation -> {
            Residency residency = invocation.getArgument(0);
            residency.setId("residency-1");
            return residency;
        });
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            return user;
        });
        when(apartmentRepository.saveAndFlush(any(Apartment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Response keycloakResponse = org.mockito.Mockito.mock(Response.class);
        when(keycloak.realm("emerald").users().create(any(UserRepresentation.class))).thenReturn(keycloakResponse);
        when(keycloakResponse.getStatus()).thenReturn(201);
        when(keycloakResponse.getLocation()).thenReturn(null);
        UserRepresentation createdUser = new UserRepresentation();
        createdUser.setUsername(request.username());
        createdUser.setId("keycloak-user-1");
        when(keycloak.realm("emerald").users().search(request.username(), true)).thenReturn(
                List.of(createdUser)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.createResidentHead(request)
        );

        assertEquals(502, exception.getErrorCode());
        assertEquals("IDENTITY_PROVIDER_ERROR", exception.getMessage());
        verify(keycloak.realm("emerald").users()).delete("keycloak-user-1");
    }

    private ResidentHeadCreateRequest request() {
        return new ResidentHeadCreateRequest(
                "Nguyễn Văn An",
                LocalDate.of(1985, 9, 20),
                Gender.MALE,
                "0901234567",
                "an.nguyen@example.com",
                null,
                "apartment-1",
                LocalDate.of(2024, 3, 1),
                "0901234567",
                "Tam@12345"
        );
    }

    private Apartment apartment(ApartmentStatus status) {
        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setStatus(status);
        return apartment;
    }
}
