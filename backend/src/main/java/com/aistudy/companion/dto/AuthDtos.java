package com.aistudy.companion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank String displayName,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String userId, String email, String displayName, String role) {}
}
