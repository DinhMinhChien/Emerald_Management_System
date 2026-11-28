package org.example.emeraldmanagementsystem.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.BaseResponse;
import org.example.emeraldmanagementsystem.dto.request.ChangePasswordRequest;
import org.example.emeraldmanagementsystem.dto.response.MeResponse;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.service.AuthService;
import org.example.emeraldmanagementsystem.service.MeService;
import org.example.emeraldmanagementsystem.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MeController {
    private final MeService meService;
    private final AuthService authService ;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<MeResponse>> getMe() {
        String username = SecurityUtils.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(401, "Chưa đăng nhập");
        }
        MeResponse response = meService.getCurrentUser(username);
        return ResponseEntity.ok(BaseResponse.ofSuccess(response, "Lấy thông tin người dùng thành công"));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        String username = SecurityUtils.getUsername() ;
        String keycloakUserId = SecurityUtils.getKeycloakUserId() ;
        authService.changePassword(username,keycloakUserId ,request) ;
        return ResponseEntity.noContent().build();
    }
}
