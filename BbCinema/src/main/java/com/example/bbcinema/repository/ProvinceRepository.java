package com.example.bbcinema.repository;

import com.example.bbcinema.model.cinema.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProvinceRepository extends JpaRepository<Province, Integer> {

    @Override
    Optional<Province> findById(Integer id);
}