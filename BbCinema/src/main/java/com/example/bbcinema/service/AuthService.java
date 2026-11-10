package com.example.bbcinema.service;

import com.example.bbcinema.dto.RegisterRequest;
import com.example.bbcinema.enums.UserStatus;
import com.example.bbcinema.model.user.Role;
import com.example.bbcinema.model.user.User;
import com.example.bbcinema.repository.RoleRepository;
import com.example.bbcinema.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String phone = request.phone().trim();

        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu xác nhận không khớp"
            );
        }

        // BCrypt giới hạn mật khẩu theo byte, không phải số ký tự.
        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu vượt quá giới hạn 72 byte"
            );
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email đã được sử dụng"
            );
        }

        if (userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Số điện thoại đã được sử dụng"
            );
        }

        Role customerRole = roleRepository.findByRoleNameIgnoreCase("Customer")
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa khởi tạo vai trò Customer"
                ));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setRole(customerRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setCurrentPoints(0);

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // Gán thêm hạng thành viên mặc định tại đây
        // nếu tier_id trong DB bắt buộc có giá trị.

        userRepository.save(user);
    }
    @Transactional(readOnly = true)
    public com.example.bbcinema.dto.UserResponse currentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        String role = user.getRole().getRoleName().trim().toUpperCase(Locale.ROOT);
        String redirect = switch (role) {
            case "ADMIN" -> "/admin.html";
            case "MANAGER" -> "/cinema-management.html";
            // Chưa có giao diện riêng cho các vai trò này.
            case "CUSTOMER", "STAFF" -> null;
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vai trò không hợp lệ");
        };
        return new com.example.bbcinema.dto.UserResponse(user.getId(), user.getFullName(), user.getEmail(), role, redirect);
    }
}
