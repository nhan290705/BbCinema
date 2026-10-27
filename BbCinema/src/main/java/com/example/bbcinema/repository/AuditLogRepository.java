package com.example.bbcinema.repository;

import com.example.bbcinema.model.management.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Override
    Optional<AuditLog> findById(Long id);
}
