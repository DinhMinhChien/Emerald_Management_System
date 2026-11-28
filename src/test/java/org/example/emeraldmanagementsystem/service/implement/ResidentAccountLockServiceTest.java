package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.emeraldmanagementsystem.dto.response.ResidentAccountLockResponse;
import org.example.emeraldmanagementsystem.entity.Resident;
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
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentAccountLockServiceTest {
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
    void disablesKeycloakAccountAndMarksLocalAccountLocked() {
        User user = user("ACTIVE");
        mockExistingResidentAndAccount(user);
        mockKeycloakUser(user.getUsername());
        UserRepresentation keycloakAccount = new UserRepresentation();
        keycloakAccount.setEnabled(true);
        var keycloakUserResource = keycloak.realm("emerald").users().get("keycloak-user-1");
        when(keycloakUserResource.toRepresentation()).thenReturn(keycloakAccount);

        ResidentAccountLockResponse response = residentService.lockResidentAccount("resident-1");

        assertEquals(new ResidentAccountLockResponse("user-1", "LOCKED"), response);
        assertEquals("LOCKED", user.getStatus());
        assertFalse(keycloakAccount.isEnabled());
        verify(keycloakUserResource).update(keycloakAccount);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void rejectsAlreadyLockedAccountWithoutCallingKeycloak() {
        User user = user("LOCKED");
        mockExistingResidentAndAccount(user);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.lockResidentAccount("resident-1")
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("ACCOUNT_ALREADY_LOCKED", exception.getMessage());
        verify(keycloak, never()).realm("emerald");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void returnsNoAccountForResidentWithoutUser() {
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(new Resident()));
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.lockResidentAccount("resident-1")
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("NO_ACCOUNT", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void returnsNotFoundForMissingResident() {
        when(residentRepository.findByIdAndDeletedFalse("missing-resident")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.lockResidentAccount("missing-resident")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("RESIDENT_NOT_FOUND", exception.getMessage());
    }

    @Test
    void keepsLocalAccountActiveWhenKeycloakLockFails() {
        User user = user("ACTIVE");
        mockExistingResidentAndAccount(user);
        mockKeycloakUser(user.getUsername());
        var keycloakUserResource = keycloak.realm("emerald").users().get("keycloak-user-1");
        UserRepresentation keycloakAccount = new UserRepresentation();
        keycloakAccount.setEnabled(true);
        when(keycloakUserResource.toRepresentation()).thenReturn(keycloakAccount);
        doThrow(new WebApplicationException(Response.status(500).build()))
                .when(keycloakUserResource)
                .update(any(UserRepresentation.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.lockResidentAccount("resident-1")
        );

        assertEquals(502, exception.getErrorCode());
        assertEquals("IDENTITY_PROVIDER_ERROR", exception.getMessage());
        assertEquals("ACTIVE", user.getStatus());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private void mockExistingResidentAndAccount(User user) {
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(new Resident()));
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(user));
    }

    private void mockKeycloakUser(String username) {
        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setUsername(username);
        keycloakUser.setId("keycloak-user-1");
        when(keycloak.realm("emerald").users().search(username, true)).thenReturn(List.of(keycloakUser));
    }

    private User user(String status) {
        User user = new User();
        user.setId("user-1");
        user.setUsername("0901234567");
        user.setStatus(status);
        return user;
    }
}
