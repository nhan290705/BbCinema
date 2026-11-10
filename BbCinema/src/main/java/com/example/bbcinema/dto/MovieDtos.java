package com.example.bbcinema.dto;

import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.validation.PosterUrl;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MovieDtos {
    private MovieDtos() {}

    public record GenreRequest(@NotBlank @Size(max = 100) String genreName) {
        public GenreRequest { genreName = trim(genreName); }
    }

    public record GenreResponse(Integer id, String genreName) {}

    public record MovieRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @NotNull @Min(1) @Max(600) Integer durationMinutes,
            @NotBlank @Pattern(regexp = "P|K|T13|T16|T18", message = "Giới hạn độ tuổi: P, K, T13, T16 hoặc T18") String ageRating,
            @Size(max = 5000) String castList,
            @Size(max = 150) String director,
            @Size(max = 255) @PosterUrl String posterUrl,
            @Size(max = 255) @URL @Pattern(regexp = "(?i)^https?://[^\\s]+$", message = "Trailer phải là URL HTTP hoặc HTTPS") String trailerUrl,
            LocalDate releaseDate,
            @NotNull MovieStatus status,
            @NotEmpty @Size(max = 30) Set<@NotNull @Positive Integer> genreIds) {
        public MovieRequest {
            title = trim(title);
            description = optional(description);
            ageRating = ageRating == null ? null : ageRating.trim().toUpperCase(Locale.ROOT);
            castList = optional(castList);
            director = optional(director);
            posterUrl = optional(posterUrl);
            trailerUrl = optional(trailerUrl);
        }
    }

    public record MovieStatusRequest(@NotNull MovieStatus status) {}

    public record MovieResponse(Long id, String title, String description, Integer durationMinutes, String ageRating,
                                String castList, String director, String posterUrl, String trailerUrl,
                                LocalDate releaseDate, MovieStatus status, List<GenreResponse> genres) {}

    public record MoviePage(List<MovieResponse> items, long totalElements, int totalPages, int page, int size) {}

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String optional(String value) {
        String result = trim(value);
        return result == null || result.isEmpty() ? null : result;
    }
}
