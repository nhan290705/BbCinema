package com.example.bbcinema.service;

import com.example.bbcinema.dto.AdminDtos.*;
import com.example.bbcinema.enums.ShowtimeStatus;
import com.example.bbcinema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class AdminOverviewService {
    private final ProvinceRepository provinces;
    private final CinemaRepository cinemas;
    private final AuditoriumRepository auditoriums;
    private final SeatRepository seats;
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final ShowtimeRepository showtimes;
    private final ShowtimePriceRepository prices;
    private final Clock clock;

    public AdminOverviewService(ProvinceRepository provinces, CinemaRepository cinemas, AuditoriumRepository auditoriums,
                                 SeatRepository seats, MovieRepository movies, GenreRepository genres,
                                 ShowtimeRepository showtimes, ShowtimePriceRepository prices, Clock clock) {
        this.provinces = provinces;
        this.cinemas = cinemas;
        this.auditoriums = auditoriums;
        this.seats = seats;
        this.movies = movies;
        this.genres = genres;
        this.showtimes = showtimes;
        this.prices = prices;
        this.clock = clock;
    }

    public Overview overview() {
        LocalDateTime now = LocalDateTime.now(clock);
        Counts counts = new Counts(provinces.count(), cinemas.count(), auditoriums.count(), seats.count(),
                movies.count(), genres.count(), showtimes.count(),
                showtimes.countByStatusAndStartTimeAfter(ShowtimeStatus.OPEN_FOR_SALE, now));
        var upcoming = showtimes.findTop6ByStatusAndStartTimeAfterOrderByStartTimeAscIdAsc(ShowtimeStatus.OPEN_FOR_SALE, now)
                .stream().map(s -> new UpcomingShowtime(s.getId(), s.getMovie().getTitle(),
                        s.getAuditorium().getCinema().getCinemaName(), s.getAuditorium().getName(), s.getStartTime(), s.getEndTime(),
                        s.getStatus(), prices.findByShowtimeIdOrderBySeatTypeIdAsc(s.getId()).stream()
                                .map(p -> new Price(p.getSeatType().getTypeName(), p.getSeatType().getSeatSpan(), p.getPrice())).toList())).toList();
        var recent = movies.findTop6ByOrderByIdDesc().stream().map(m -> new RecentMovie(m.getId(), m.getTitle(),
                m.getPosterUrl(), m.getStatus(), m.getDurationMinutes(), m.getAgeRating())).toList();
        return new Overview(counts, upcoming, recent, now);
    }
}
