package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeatTypeRepository extends JpaRepository<SeatType, Integer> {
    boolean existsByTypeNameIgnoreCase(String typeName);

    Optional<SeatType> findByTypeNameIgnoreCase(String typeName);

    @Override
    Optional<SeatType> findById(Integer id);
}
