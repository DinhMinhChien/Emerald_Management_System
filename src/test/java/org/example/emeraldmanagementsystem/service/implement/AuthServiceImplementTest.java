package org.example.emeraldmanagementsystem.service.implement;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.example.emeraldmanagementsystem.dto.request.ChangePasswordRequest;
import org.example.emeraldmanagementsystem.dto.request.UserLoginRequest;
import org.example.emeraldmanagementsystem.entity.User;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplementTest {
    private static final String USERNAME = "resident";
    private static final String KEYCLOAK_USER_ID = "keycloak-user-id";

    @Test
    void changePasswordReturns422ForSamePasswordWithoutAuthenticating() {
        UserRepository userRepository = mock(UserRepository.class);
        Keycloak keycloak = mock(Keycloak.class);
        AuthServiceImplement service = createService(userRepository, keycloak);
        when(userRepository.findUserByUsernameAndDeletedFalse(USERNAME))
                .thenReturn(Optional.of(user()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        USERNAME,
                        KEYCLOAK_USER_ID,
                        new ChangePasswordRequest("same", "same")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("SAME_PASSWORD", exception.getMessage());
        verify(keycloak, never()).realm(any());
    }

    @Test
    void changePasswordReturns422WhenCurrentPasswordIsWrong() {
        UserRepository userRepository = mock(UserRepository.class);
        Keycloak keycloak = mock(Keycloak.class);
        AuthServiceImplement service = createService(userRepository, keycloak);
        when(userRepository.findUserByUsernameAndDeletedFalse(USERNAME))
                .thenReturn(Optional.of(user()));
        WebClientResponseException badRequest = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid credentials",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        doThrow(badRequest).when(service).login(any(UserLoginRequest.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        USERNAME,
                        KEYCLOAK_USER_ID,
                        new ChangePasswordRequest("wrong", "new")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("WRONG_CURRENT_PASSWORD", exception.getMessage());
        verify(keycloak, never()).realm(any());
    }

    @Test
    void changePasswordMapsAuthenticationServiceFailureTo502() {
        UserRepository userRepository = mock(UserRepository.class);
        Keycloak keycloak = mock(Keycloak.class);
        AuthServiceImplement service = createService(userRepository, keycloak);
        when(userRepository.findUserByUsernameAndDeletedFalse(USERNAME))
                .thenReturn(Optional.of(user()));
        WebClientResponseException unauthorized = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid client credentials",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        doThrow(unauthorized).when(service).login(any(UserLoginRequest.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        USERNAME,
                        KEYCLOAK_USER_ID,
                        new ChangePasswordRequest("old", "new")
                )
        );

        assertEquals(502, exception.getErrorCode());
        assertEquals("IDENTITY_PROVIDER_ERROR", exception.getMessage());
    }

    @Test
    void changePasswordMapsKeycloakPolicyFailureToWeakPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        Keycloak keycloak = mock(Keycloak.class);
        AuthServiceImplement service = createService(userRepository, keycloak);
        User user = user();
        when(userRepository.findUserByUsernameAndDeletedFalse(USERNAME)).thenReturn(Optional.of(user));
        allowCurrentPasswordVerification(service);

        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        UserResource userResource = mock(UserResource.class);
        when(keycloak.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(KEYCLOAK_USER_ID)).thenReturn(userResource);
        doThrow(new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build()))
                .when(userResource).resetPassword(any());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        USERNAME,
                        KEYCLOAK_USER_ID,
                        new ChangePasswordRequest("old", "weak")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("WEAK_PASSWORD", exception.getMessage());
        assertTrue(user.isMustChangePassword());
        verify(service).logout("verification-refresh-token");
    }

    @Test
    void successfulChangeClearsMustChangePassword() {
        UserRepository userRepository = mock(UserRepository.class);
        Keycloak keycloak = mock(Keycloak.class);
        AuthServiceImplement service = createService(userRepository, keycloak);
        User user = user();
        when(userRepository.findUserByUsernameAndDeletedFalse(USERNAME)).thenReturn(Optional.of(user));
        allowCurrentPasswordVerification(service);

        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        UserResource userResource = mock(UserResource.class);
        when(keycloak.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(KEYCLOAK_USER_ID)).thenReturn(userResource);

        service.changePassword(
                USERNAME,
                KEYCLOAK_USER_ID,
                new ChangePasswordRequest("old", "new")
        );

        assertFalse(user.isMustChangePassword());
        verify(userRepository).save(user);
        verify(userResource).resetPassword(any());
        verify(service).logout("verification-refresh-token");
    }

    private AuthServiceImplement createService(UserRepository userRepository, Keycloak keycloak) {
        AuthServiceImplement service = spy(
                new AuthServiceImplement(userRepository, keycloak, mock(WebClient.Builder.class))
        );
        ReflectionTestUtils.setField(service, "realm", "test-realm");
        return service;
    }

    private void allowCurrentPasswordVerification(AuthServiceImplement service) {
        doReturn(Map.of("refresh_token", "verification-refresh-token"))
                .when(service).login(any(UserLoginRequest.class));
        doNothing().when(service).logout("verification-refresh-token");
    }

    private User user() {
        User user = new User();
        user.setMustChangePassword(true);
        return user;
    }
}
