package com.example.bbcinema.dto;

import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.enums.ShowtimeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record Counts(long provinces, long cinemas, long auditoriums, long seats,
                         long movies, long genres, long showtimes, long openShowtimes) {}
    public record Price(String seatTypeName, Integer seatSpan, BigDecimal price) {}
    public record UpcomingShowtime(Long id, String movieTitle, String cinemaName, String auditoriumName,
                                   LocalDateTime startTime, LocalDateTime endTime, ShowtimeStatus status,
                                   List<Price> prices) {}
    public record RecentMovie(Long id, String title, String posterUrl, MovieStatus status,
                               Integer durationMinutes, String ageRating) {}
    public record Overview(Counts counts, List<UpcomingShowtime> upcomingShowtimes,
                           List<RecentMovie> recentMovies, LocalDateTime generatedAt) {}
}
