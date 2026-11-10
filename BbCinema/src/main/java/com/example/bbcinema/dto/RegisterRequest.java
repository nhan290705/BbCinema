package com.example.bbcinema.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 120)
        String email,

        @NotBlank
        @Pattern(regexp = "^0[0-9]{9}$",
                message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0")
        String phone,

        @NotBlank
        @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
        String password,

        @NotBlank
        String confirmPassword
) {
    public RegisterRequest {
        if (fullName != null) fullName = fullName.trim();
        if (email != null) email = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (phone != null) phone = phone.trim();
    }
}

