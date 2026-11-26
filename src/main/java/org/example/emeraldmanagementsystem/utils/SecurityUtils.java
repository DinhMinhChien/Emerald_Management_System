package org.example.emeraldmanagementsystem.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth && authentication.isAuthenticated()) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }

    public static String getKeycloakUserId() {
        return getCurrentJwt()
                .map(Jwt::getSubject)
                .orElse(null);
    }

    public static String getUsername() {
        return getCurrentJwt()
                .map(jwt -> {
                    String username = jwt.getClaimAsString("preferred_username");
                    return username != null ? username : jwt.getSubject();
                })
                .orElse(null);
    }

    public static Collection<? extends GrantedAuthority> getAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptyList();
        }
        return authentication.getAuthorities();
    }
}
