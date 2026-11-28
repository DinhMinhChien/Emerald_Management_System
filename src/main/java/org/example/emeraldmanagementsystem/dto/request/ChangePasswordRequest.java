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
public class ChangePasswordRequest {
    @NotBlank(message = "currentPassword không được để trống")
    private String currentPassword ;

    @NotBlank(message = "newPassword không được để trống")
    private String newPassword ;
}