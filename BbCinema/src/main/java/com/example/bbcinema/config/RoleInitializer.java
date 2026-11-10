package com.example.bbcinema.config;

import com.example.bbcinema.model.user.Role;
import com.example.bbcinema.repository.RoleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

@Configuration
public class RoleInitializer {
    @Bean
    public ApplicationRunner initializeRoles(RoleRepository roles) {
        return args -> {
            for (String name : new String[]{"Customer", "Staff", "Manager", "Admin"}) {
                if (roles.findByRoleNameIgnoreCase(name).isEmpty()) {
                    Role role = new Role(); role.setRoleName(name); roles.save(role);
                }
            }
        };
    }
}
