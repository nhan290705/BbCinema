package com.example.bbcinema.repository;

import com.example.bbcinema.model.movie.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    java.util.List<Movie> findTop6ByOrderByIdDesc();
    boolean existsByGenresId(Integer genreId);

    @Override
    Optional<Movie> findById(Long id);
}
