package com.servicepoint.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class LoginRequest {

    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "role is required")
    private String role;

    // NEW: OTP code for login verification
//    @NotBlank(message = "OTP code is required")
//    @Pattern(regexp = "\\d{6}", message = "OTP must be a 6-digit number")
//    private String otpCode;
}