package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.AdminOverviewController;
import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.enums.ShowtimeStatus;
import com.example.bbcinema.model.cinema.Auditorium;
import com.example.bbcinema.model.cinema.Cinema;
import com.example.bbcinema.model.cinema.SeatType;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.model.movie.Showtime;
import com.example.bbcinema.model.movie.ShowtimePrice;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.AdminOverviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AdminOverviewTest.Config.class)
@WebAppConfiguration
class AdminOverviewTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 7, 0);

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, AdminOverviewController.class, AdminOverviewService.class})
    static class Config {
        @Bean ProvinceRepository provinces() { return mock(ProvinceRepository.class); }
        @Bean CinemaRepository cinemas() { return mock(CinemaRepository.class); }
        @Bean AuditoriumRepository auditoriums() { return mock(AuditoriumRepository.class); }
        @Bean SeatRepository seats() { return mock(SeatRepository.class); }
        @Bean MovieRepository movies() { return mock(MovieRepository.class); }
        @Bean GenreRepository genres() { return mock(GenreRepository.class); }
        @Bean ShowtimeRepository showtimes() { return mock(ShowtimeRepository.class); }
        @Bean ShowtimePriceRepository prices() { return mock(ShowtimePriceRepository.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired ProvinceRepository provinces;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired SeatRepository seats;
    @Autowired MovieRepository movies;
    @Autowired GenreRepository genres;
    @Autowired ShowtimeRepository showtimes;
    @Autowired ShowtimePriceRepository prices;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(provinces, cinemas, auditoriums, seats, movies, genres, showtimes, prices);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
        when(showtimes.findTop6ByStatusAndStartTimeAfterOrderByStartTimeAscIdAsc(ShowtimeStatus.OPEN_FOR_SALE, NOW))
                .thenReturn(List.of());
        when(movies.findTop6ByOrderByIdDesc()).thenReturn(List.of());
    }

    @Test
    void overviewRequiresAnAuthenticatedAdmin() throws Exception {
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        for (String role : List.of("CUSTOMER", "STAFF", "MANAGER")) {
            mvc.perform(get("/api/admin/overview").with(user("account").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(provinces, cinemas, auditoriums, seats, movies, genres, showtimes, prices);
    }

    @Test
    void populatedOverviewUsesDatabaseCountsAndPreservesFinalCouplePrice() throws Exception {
        when(provinces.count()).thenReturn(3L);
        when(cinemas.count()).thenReturn(4L);
        when(auditoriums.count()).thenReturn(8L);
        when(seats.count()).thenReturn(440L);
        when(movies.count()).thenReturn(6L);
        when(genres.count()).thenReturn(5L);
        when(showtimes.count()).thenReturn(24L);
        when(showtimes.countByStatusAndStartTimeAfter(ShowtimeStatus.OPEN_FOR_SALE, NOW)).thenReturn(7L);

        Movie movie = new Movie();
        movie.setId(42L); movie.setTitle("Thành phố sau cơn mưa");
        movie.setPosterUrl("/demo/posters/rain-city.svg");
        movie.setDurationMinutes(120); movie.setAgeRating("T13"); movie.setStatus(MovieStatus.NOW_SHOWING);
        Cinema cinema = new Cinema(); cinema.setId(10L); cinema.setCinemaName("Bbcinema Central (Mẫu)");
        Auditorium room = new Auditorium(); room.setId(20L); room.setCinema(cinema); room.setName("Phòng 1");
        Showtime showtime = new Showtime(); showtime.setId(30L); showtime.setMovie(movie); showtime.setAuditorium(room);
        showtime.setStartTime(NOW.plusHours(2)); showtime.setEndTime(NOW.plusHours(4));
        showtime.setStatus(ShowtimeStatus.OPEN_FOR_SALE);
        SeatType couple = new SeatType(); couple.setId(3); couple.setTypeName("COUPLE");
        couple.setSeatSpan(2); couple.setDefaultSurcharge(new BigDecimal("50000"));
        ShowtimePrice price = new ShowtimePrice(); price.setId(100L); price.setShowtime(showtime); price.setSeatType(couple);
        price.setPrice(new BigDecimal("150000"));
        when(showtimes.findTop6ByStatusAndStartTimeAfterOrderByStartTimeAscIdAsc(ShowtimeStatus.OPEN_FOR_SALE, NOW))
                .thenReturn(List.of(showtime));
        when(prices.findByShowtimeIdOrderBySeatTypeIdAsc(30L)).thenReturn(List.of(price));
        when(movies.findTop6ByOrderByIdDesc()).thenReturn(List.of(movie));

        mvc.perform(get("/api/admin/overview").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.provinces").value(3))
                .andExpect(jsonPath("$.counts.cinemas").value(4))
                .andExpect(jsonPath("$.counts.auditoriums").value(8))
                .andExpect(jsonPath("$.counts.seats").value(440))
                .andExpect(jsonPath("$.counts.movies").value(6))
                .andExpect(jsonPath("$.counts.genres").value(5))
                .andExpect(jsonPath("$.counts.showtimes").value(24))
                .andExpect(jsonPath("$.counts.openShowtimes").value(7))
                .andExpect(jsonPath("$.upcomingShowtimes[0].id").value(30))
                .andExpect(jsonPath("$.upcomingShowtimes[0].movieTitle").value(movie.getTitle()))
                .andExpect(jsonPath("$.upcomingShowtimes[0].cinemaName").value(cinema.getCinemaName()))
                .andExpect(jsonPath("$.upcomingShowtimes[0].auditoriumName").value("Phòng 1"))
                .andExpect(jsonPath("$.upcomingShowtimes[0].prices[0].seatSpan").value(2))
                .andExpect(jsonPath("$.upcomingShowtimes[0].prices[0].price").value(150000))
                .andExpect(jsonPath("$.recentMovies[0].posterUrl").value("/demo/posters/rain-city.svg"))
                .andExpect(jsonPath("$.generatedAt").exists());

        verify(showtimes).countByStatusAndStartTimeAfter(ShowtimeStatus.OPEN_FOR_SALE, NOW);
        verify(showtimes).findTop6ByStatusAndStartTimeAfterOrderByStartTimeAscIdAsc(ShowtimeStatus.OPEN_FOR_SALE, NOW);
        verify(movies).findTop6ByOrderByIdDesc();
        verify(showtimes, never()).findAll();
        verify(movies, never()).findAll();
    }

    @Test
    void emptyDatabaseReturnsZeroCountsAndEmptyCollectionsInsteadOfInventedData() throws Exception {
        mvc.perform(get("/api/admin/overview").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.movies").value(0))
                .andExpect(jsonPath("$.counts.cinemas").value(0))
                .andExpect(jsonPath("$.counts.openShowtimes").value(0))
                .andExpect(jsonPath("$.upcomingShowtimes").isEmpty())
                .andExpect(jsonPath("$.recentMovies").isEmpty())
                .andExpect(jsonPath("$.generatedAt").exists());
        verify(prices, never()).findByShowtimeIdOrderBySeatTypeIdAsc(any());
    }
}
