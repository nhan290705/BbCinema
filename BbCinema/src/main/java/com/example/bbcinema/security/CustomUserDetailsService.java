package com.example.bbcinema.security;

import com.example.bbcinema.enums.UserStatus;
import com.example.bbcinema.model.user.User;
import com.example.bbcinema.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCase(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new UsernameNotFoundException("Tài khoản không hợp lệ")
        );

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().getRoleName().trim().toUpperCase(Locale.ROOT))
                .disabled(user.getStatus() != UserStatus.ACTIVE)
                .build();
    }
}

