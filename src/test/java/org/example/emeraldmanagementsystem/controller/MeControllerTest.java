package org.example.emeraldmanagementsystem.controller;

import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.ChangePasswordRequest;
import org.example.emeraldmanagementsystem.dto.response.MeResponse;
import org.example.emeraldmanagementsystem.service.AuthService;
import org.example.emeraldmanagementsystem.service.MeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeControllerTest {
    @Test
    void getMeWrapsResponseInBaseResponse() {
        MeService meService = mock(MeService.class);
        MeResponse meResponse = new MeResponse(
                "MANAGER", "Manager Name", false, null
        );
        when(meService.getCurrentUser("manager")).thenReturn(meResponse);
        MeController controller = new MeController(meService, mock(AuthService.class));
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.MAX,
                Map.of("alg", "none"),
                Map.of("sub", "keycloak-user-id", "preferred_username", "manager")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
        );

        try {
            var response = controller.getMe();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            BaseResponse<MeResponse> body = response.getBody();
            assertSame(meResponse, body.getData());
            assertEquals(200, body.getMeta().getCode());
            assertEquals("Lấy thông tin người dùng thành công", body.getMeta().getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void changePasswordReturnsNoContent() {
        MeService meService = mock(MeService.class);
        AuthService authService = mock(AuthService.class);
        MeController controller = new MeController(meService, authService);
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.MAX,
                Map.of("alg", "none"),
                Map.of("sub", "keycloak-user-id", "preferred_username", "manager")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
        );

        try {
            ChangePasswordRequest request = new ChangePasswordRequest("old", "new");
            var response = controller.changePassword(request);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            assertNull(response.getBody());
            verify(authService).changePassword(
                    "manager",
                    "keycloak-user-id",
                    request
            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
