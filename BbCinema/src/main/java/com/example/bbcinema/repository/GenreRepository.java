package com.example.bbcinema.repository;

import com.example.bbcinema.model.movie.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Integer> {
    boolean existsByGenreNameIgnoreCase(String genreName);

    boolean existsByGenreNameIgnoreCaseAndIdNot(String genreName, Integer id);

    @Override
    Optional<Genre> findById(Integer id);
}
