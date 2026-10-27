package com.example.bbcinema.repository;

import com.example.bbcinema.model.user.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PointHistoryRepository
        extends JpaRepository<PointHistory, Long> {

    @Override
    Optional<PointHistory> findById(Long id);
}