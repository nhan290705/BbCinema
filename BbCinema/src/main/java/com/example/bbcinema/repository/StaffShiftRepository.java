package com.example.bbcinema.repository;

import com.example.bbcinema.model.management.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

    @Override
    Optional<StaffShift> findById(Long id);
}
