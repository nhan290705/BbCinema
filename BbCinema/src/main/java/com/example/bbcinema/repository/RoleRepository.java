package com.example.bbcinema.repository;

import com.example.bbcinema.model.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    @Override
    Optional<Role> findById(Integer id);
}
