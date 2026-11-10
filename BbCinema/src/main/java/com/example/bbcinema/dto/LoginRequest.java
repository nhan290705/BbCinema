package com.example.bbcinema.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email @Size(max = 120) String email,
                           @NotBlank @Size(max = 72) String password) {
    public LoginRequest {
        if (email != null) email = email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @AssertTrue(message = "Mật khẩu vượt quá giới hạn 72 byte")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
