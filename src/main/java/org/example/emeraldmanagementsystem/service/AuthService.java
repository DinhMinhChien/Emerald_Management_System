package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.request.ChangePasswordRequest;
import org.example.emeraldmanagementsystem.dto.request.TokenRefreshRequest;
import org.example.emeraldmanagementsystem.dto.request.UserLoginRequest;

import java.util.Map;

public interface AuthService {
    Map<String,Object> login(UserLoginRequest request) ;
    Map<String,Object> refreshToken(TokenRefreshRequest request) ;
    void logout(String refreshToken);
    void changePassword(String username, String keycloakUserId, ChangePasswordRequest request) ;
}
