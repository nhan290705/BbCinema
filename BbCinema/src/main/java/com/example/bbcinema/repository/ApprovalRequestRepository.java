package com.example.bbcinema.repository;

import com.example.bbcinema.model.management.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, Long> {

    @Override
    Optional<ApprovalRequest> findById(Long id);
}
