package org.example.emeraldmanagementsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.TokenRefreshRequest;
import org.example.emeraldmanagementsystem.dto.request.UserLoginRequest;
import org.example.emeraldmanagementsystem.dto.request.UserLogoutRequest;
import org.example.emeraldmanagementsystem.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService ;

    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@RequestBody UserLoginRequest request) {
        Map<String,Object> response = authService.login(request) ;
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String,Object>> refreshToken(@RequestBody TokenRefreshRequest request) {
        Map<String,Object> response = authService.refreshToken(request) ;
        return ResponseEntity.ok(response) ;
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody @Valid UserLogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok("Đăng xuất thành công!");
    }

}
