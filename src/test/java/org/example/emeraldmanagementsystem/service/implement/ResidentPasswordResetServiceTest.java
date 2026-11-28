package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.emeraldmanagementsystem.dto.request.ResidentPasswordResetRequest;
import org.example.emeraldmanagementsystem.dto.response.ResidentPasswordResetResponse;
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
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidentPasswordResetServiceTest {
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
    void resetsPasswordAndRequiresChangeOnNextLogin() {
        User user = user(false, "ACTIVE");
        mockExistingResidentAndAccount(user);
        mockKeycloakUser(user.getUsername());

        ResidentPasswordResetResponse response = residentService.resetResidentPassword(
                "resident-1",
                new ResidentPasswordResetRequest("Temp@12345")
        );

        assertEquals(new ResidentPasswordResetResponse("user-1", "Temp@12345", true), response);
        assertTrue(user.isMustChangePassword());
        ArgumentCaptor<CredentialRepresentation> credentialCaptor =
                ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(keycloak.realm("emerald").users().get("keycloak-user-1"))
                .resetPassword(credentialCaptor.capture());
        CredentialRepresentation credential = credentialCaptor.getValue();
        assertEquals(CredentialRepresentation.PASSWORD, credential.getType());
        assertEquals("Temp@12345", credential.getValue());
        assertTrue(credential.isTemporary());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void generatesSecureTemporaryPasswordWhenNotProvided() {
        User user = user(false, "ACTIVE");
        mockExistingResidentAndAccount(user);
        mockKeycloakUser(user.getUsername());

        ResidentPasswordResetResponse response = residentService.resetResidentPassword("resident-1", null);

        String password = response.temporaryPassword();
        assertEquals(20, password.length());
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(character -> "!@#$%&*?".indexOf(character) >= 0));
        assertTrue(response.mustChangePassword());
        assertTrue(user.isMustChangePassword());
        ArgumentCaptor<CredentialRepresentation> credentialCaptor =
                ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(keycloak.realm("emerald").users().get("keycloak-user-1"))
                .resetPassword(credentialCaptor.capture());
        assertEquals(password, credentialCaptor.getValue().getValue());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void returnsNoAccountForResidentWithoutUser() {
        when(residentRepository.findByIdAndDeletedFalse("resident-1")).thenReturn(Optional.of(new Resident()));
        when(userRepository.findByResidentIdAndDeletedFalse("resident-1")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.resetResidentPassword(
                        "resident-1",
                        new ResidentPasswordResetRequest("Temp@12345")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("NO_ACCOUNT", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsLockedAccountBeforeCallingKeycloak() {
        User user = user(false, "LOCKED");
        mockExistingResidentAndAccount(user);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.resetResidentPassword(
                        "resident-1",
                        new ResidentPasswordResetRequest("Temp@12345")
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("ACCOUNT_LOCKED", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        assertFalse(user.isMustChangePassword());
    }

    @Test
    void mapsKeycloakPasswordPolicyFailure() {
        User user = user(false, "ACTIVE");
        mockExistingResidentAndAccount(user);
        mockKeycloakUser(user.getUsername());
        var keycloakUser = keycloak.realm("emerald").users().get("keycloak-user-1");
        doThrow(new WebApplicationException(Response.status(400).build()))
                .when(keycloakUser)
                .resetPassword(any(CredentialRepresentation.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> residentService.resetResidentPassword(
                        "resident-1",
                        new ResidentPasswordResetRequest("weak")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("WEAK_PASSWORD", exception.getMessage());
        assertFalse(user.isMustChangePassword());
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

    private User user(boolean mustChangePassword, String status) {
        User user = new User();
        user.setId("user-1");
        user.setUsername("0901234567");
        user.setMustChangePassword(mustChangePassword);
        user.setStatus(status);
        return user;
    }
}
