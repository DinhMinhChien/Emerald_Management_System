package org.example.emeraldmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLogoutRequest {
    @NotBlank(message = "Refresh Token không được để trống")
    private String refreshToken;
}

