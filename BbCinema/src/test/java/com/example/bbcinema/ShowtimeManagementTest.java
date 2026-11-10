package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.ShowtimeAdminController;
import com.example.bbcinema.controller.ShowtimeCatalogController;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.model.movie.Showtime;
import com.example.bbcinema.model.movie.ShowtimePrice;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.ShowtimeManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ShowtimeManagementTest.Config.class)
@WebAppConfiguration
class ShowtimeManagementTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, ShowtimeCatalogController.class,
            ShowtimeAdminController.class, ShowtimeManagementService.class, ApiExceptionHandler.class})
    static class Config {
        @Bean MovieRepository movies() { return mock(MovieRepository.class); }
        @Bean CinemaRepository cinemas() { return mock(CinemaRepository.class); }
        @Bean AuditoriumRepository auditoriums() { return mock(AuditoriumRepository.class); }
        @Bean SeatRepository seats() { return mock(SeatRepository.class); }
        @Bean ShowtimeRepository showtimes() { return mock(ShowtimeRepository.class); }
        @Bean ShowtimePriceRepository prices() { return mock(ShowtimePriceRepository.class); }
        @Bean TicketRepository tickets() { return mock(TicketRepository.class); }
        @Bean SeatHoldRepository holds() { return mock(SeatHoldRepository.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired MovieRepository movies;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired SeatRepository seats;
    @Autowired ShowtimeRepository showtimes;
    @Autowired ShowtimePriceRepository prices;
    @Autowired TicketRepository tickets;
    @Autowired SeatHoldRepository holds;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    Movie movie;
    Cinema cinema;
    Auditorium room;
    Showtime showtime;
    List<Seat> roomSeats;
    Map<Long, List<ShowtimePrice>> storedPrices;

    @BeforeEach
    void setup() {
        reset(movies, cinemas, auditoriums, seats, showtimes, prices, tickets, holds);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
        movie = new Movie(); movie.setId(1L); movie.setTitle("Phim kiểm thử");
        movie.setDurationMinutes(120); movie.setAgeRating("T13"); movie.setStatus(MovieStatus.NOW_SHOWING);
        cinema = new Cinema(); cinema.setId(10L); cinema.setCinemaName("Rạp kiểm thử"); cinema.setStatus(CinemaStatus.ACTIVE);
        room = new Auditorium(); room.setId(20L); room.setName("Phòng 1"); room.setCinema(cinema);
        room.setTotalRows(2); room.setTotalCols(4); room.setStatus(AuditoriumStatus.ACTIVE);
        SeatType standard = type(1, "STANDARD", 1, "0");
        SeatType vip = type(2, "VIP", 1, "10000");
        SeatType couple = type(3, "COUPLE", 2, "50000");
        roomSeats = new ArrayList<>(List.of(
                seat(101, standard, 0, 0, SeatStatus.AVAILABLE),
                seat(102, vip, 1, 0, SeatStatus.AVAILABLE),
                seat(103, couple, 2, 0, SeatStatus.AVAILABLE),
                seat(104, standard, 0, 1, SeatStatus.BROKEN),
                seat(105, vip, 1, 1, SeatStatus.BLOCKED),
                seat(106, standard, 2, 1, SeatStatus.AISLE)));
        showtime = new Showtime(); showtime.setId(30L); showtime.setMovie(movie); showtime.setAuditorium(room);
        showtime.setStartTime(LocalDateTime.of(2030, 1, 2, 14, 0));
        showtime.setEndTime(LocalDateTime.of(2030, 1, 2, 16, 0));
        showtime.setBasePrice(new BigDecimal("100000")); showtime.setStatus(ShowtimeStatus.OPEN_FOR_SALE);
        storedPrices = new HashMap<>();
        storedPrices.put(30L, new ArrayList<>(List.of(price(showtime, standard, "100000"),
                price(showtime, vip, "120000"), price(showtime, couple, "180000"))));
        when(movies.findById(1L)).thenReturn(Optional.of(movie));
        when(cinemas.findById(10L)).thenReturn(Optional.of(cinema));
        Cinema otherCinema = new Cinema(); otherCinema.setId(11L); otherCinema.setCinemaName("Rạp khác");
        otherCinema.setStatus(CinemaStatus.ACTIVE);
        when(cinemas.findById(11L)).thenReturn(Optional.of(otherCinema));
        when(auditoriums.findById(20L)).thenReturn(Optional.of(room));
        when(auditoriums.findByIdForUpdate(20L)).thenReturn(Optional.of(room));
        when(seats.findByAuditoriumIdOrderByGridYAscGridXAsc(20L)).thenAnswer(inv -> roomSeats);
        when(showtimes.findById(30L)).thenReturn(Optional.of(showtime));
        when(showtimes.findByIdForUpdate(30L)).thenReturn(Optional.of(showtime));
        when(showtimes.findAuditoriumIdById(30L)).thenReturn(Optional.of(20L));
        when(showtimes.save(any(Showtime.class))).thenAnswer(inv -> {
            Showtime value = inv.getArgument(0);
            if (value.getId() == null) value.setId(31L);
            return value;
        });
        when(showtimes.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(showtime), inv.getArgument(1), 1));
        when(prices.findByShowtimeIdOrderBySeatTypeIdAsc(anyLong())).thenAnswer(inv ->
                storedPrices.getOrDefault(inv.getArgument(0), List.of()));
        when(prices.saveAll(any())).thenAnswer(inv -> {
            List<ShowtimePrice> result = new ArrayList<>();
            List<ShowtimePrice> input = new ArrayList<>();
            for (ShowtimePrice price : (Iterable<ShowtimePrice>) inv.getArgument(0)) input.add(price);
            for (ShowtimePrice price : input) {
                if (price.getId() == null) price.setId(1000L + result.size());
                List<ShowtimePrice> existing = storedPrices.computeIfAbsent(price.getShowtime().getId(), k -> new ArrayList<>());
                existing.removeIf(p -> p.getSeatType().getId().equals(price.getSeatType().getId()));
                existing.add(price); result.add(price);
            }
            return result;
        });
    }

    private SeatType type(int id, String name, int span, String surcharge) {
        SeatType type = new SeatType(); type.setId(id); type.setTypeName(name); type.setSeatSpan(span);
        type.setDefaultSurcharge(new BigDecimal(surcharge)); return type;
    }
    private Seat seat(long id, SeatType type, int x, int y, SeatStatus status) {
        Seat seat = new Seat(); seat.setId(id); seat.setAuditorium(room); seat.setSeatType(type);
        seat.setGridX(x); seat.setGridY(y); seat.setRowLabel(y == 0 ? "A" : "B"); seat.setColNumber(x + 1); seat.setStatus(status);
        return seat;
    }
    private ShowtimePrice price(Showtime show, SeatType type, String amount) {
        ShowtimePrice price = new ShowtimePrice(); price.setId(type.getId().longValue());
        price.setShowtime(show); price.setSeatType(type); price.setPrice(new BigDecimal(amount)); return price;
    }
    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("movieId", 1); body.put("cinemaId", 10); body.put("auditoriumId", 20);
        body.put("startTime", "2030-01-02T17:00:00"); body.put("basePrice", 100000);
        body.put("prices", List.of(Map.of("seatTypeId", 1, "price", 100000),
                Map.of("seatTypeId", 2, "price", 120000), Map.of("seatTypeId", 3, "price", 180000)));
        return body;
    }
    private void create(Map<String, Object> body, int expectedStatus) throws Exception {
        mvc.perform(post("/api/admin/showtimes").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }
    private void patchStatus(String value, int expectedStatus) throws Exception {
        mvc.perform(patch("/api/admin/showtimes/30/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void publicCanViewSaleAndSeatPricesWithoutSession() throws Exception {
        mvc.perform(get("/api/showtimes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(30));
        mvc.perform(get("/api/showtimes/30")).andExpect(status().isOk())
                .andExpect(jsonPath("$.prices.length()").value(3));
        mvc.perform(get("/api/showtimes/30/seat-map")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[2].seatSpan").value(2))
                .andExpect(jsonPath("$.seats[2].gridX").value(2))
                .andExpect(jsonPath("$.seats[2].price").value(180000))
                .andExpect(jsonPath("$.seats[2].usable").value(true))
                .andExpect(jsonPath("$.seats[3].usable").value(false))
                .andExpect(jsonPath("$.seats[4].usable").value(false))
                .andExpect(jsonPath("$.seats[5].usable").value(false));
    }

    @Test
    void unsaleableShowsAndHiddenMoviesCannotBeReadPublicly() throws Exception {
        for (ShowtimeStatus value : List.of(ShowtimeStatus.SCHEDULED, ShowtimeStatus.CANCELLED, ShowtimeStatus.COMPLETED)) {
            showtime.setStatus(value);
            mvc.perform(get("/api/showtimes/30")).andExpect(status().isNotFound());
            mvc.perform(get("/api/showtimes/30/seat-map")).andExpect(status().isNotFound());
        }
        showtime.setStatus(ShowtimeStatus.OPEN_FOR_SALE); movie.setStatus(MovieStatus.HIDDEN);
        mvc.perform(get("/api/showtimes/30")).andExpect(status().isNotFound());
        movie.setStatus(MovieStatus.NOW_SHOWING); room.setStatus(AuditoriumStatus.MAINTENANCE);
        mvc.perform(get("/api/showtimes/30")).andExpect(status().isNotFound());
        room.setStatus(AuditoriumStatus.ACTIVE); showtime.setStartTime(LocalDateTime.of(2030, 1, 1, 6, 0));
        mvc.perform(get("/api/showtimes/30")).andExpect(status().isNotFound());
    }

    @Test
    void adminAndCsrfRequiredForChanges() throws Exception {
        mvc.perform(get("/api/admin/showtimes")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "MANAGER")) {
            mvc.perform(post("/api/admin/showtimes").with(user("account").roles(role)).with(csrf())
                    .contentType("application/json").content(mapper.writeValueAsString(body())))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/showtimes").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isForbidden());
        verify(showtimes, never()).save(any());
    }

    @Test
    void creationDerivesEndFromDurationAndSavesExplicitPrices() throws Exception {
        create(body(), 201);
        verify(showtimes).save(argThat(s -> s.getMovie() == movie && s.getAuditorium() == room
                && s.getStatus() == ShowtimeStatus.SCHEDULED
                && s.getStartTime().equals(LocalDateTime.of(2030, 1, 2, 17, 0))
                && s.getEndTime().equals(LocalDateTime.of(2030, 1, 2, 19, 0))));
        List<ShowtimePrice> saved = storedPrices.get(31L);
        assertEquals(3, saved.size());
        assertEquals(0, saved.stream().filter(p -> p.getSeatType().getId() == 3).findFirst().orElseThrow()
                .getPrice().compareTo(new BigDecimal("180000")));
        verify(auditoriums).findByIdForUpdate(20L);
        verify(showtimes).hasOverlap(20L, LocalDateTime.of(2030, 1, 2, 17, 0), LocalDateTime.of(2030, 1, 2, 19, 0), 0L);
    }

    @Test
    void overlappingScheduleReturnsConflictWithoutSaving() throws Exception {
        when(showtimes.hasOverlap(eq(20L), any(), any(), eq(0L))).thenReturn(true);
        create(body(), 409);
        verify(showtimes, never()).save(any()); verify(prices, never()).saveAll(any());
    }

    @Test
    void pricesMustCoverEveryPhysicalTypeExactlyOnce() throws Exception {
        for (List<Map<String, Integer>> input : List.of(
                List.of(Map.of("seatTypeId", 1, "price", 100000)),
                List.of(Map.of("seatTypeId", 1, "price", 100000), Map.of("seatTypeId", 1, "price", 110000)),
                List.of(Map.of("seatTypeId", 1, "price", 100000), Map.of("seatTypeId", 2, "price", 120000),
                        Map.of("seatTypeId", 3, "price", 180000), Map.of("seatTypeId", 999, "price", 200000)))) {
            Map<String, Object> request = body(); request.put("prices", input); create(request, 400);
        }
        verify(showtimes, never()).save(any());
    }

    @Test
    void inactiveResourcesMismatchedCinemaAndPastStartAreRejected() throws Exception {
        Map<String, Object> request = body(); request.put("startTime", "2030-01-01T06:59:00"); create(request, 400);
        request = body(); request.put("cinemaId", 11); create(request, 400);
        cinema.setStatus(CinemaStatus.MAINTENANCE); create(body(), 409);
        cinema.setStatus(CinemaStatus.ACTIVE); room.setStatus(AuditoriumStatus.INACTIVE); create(body(), 409);
        room.setStatus(AuditoriumStatus.ACTIVE); movie.setStatus(MovieStatus.ENDED); create(body(), 409);
        verify(showtimes, never()).save(any());
    }

    @Test
    void roomNeedsPhysicalSeatsAndOpeningNeedsAvailableSeat() throws Exception {
        roomSeats = List.of(); create(body(), 400);
        roomSeats = List.of(seat(201, type(1, "STANDARD", 1, "0"), 0, 0, SeatStatus.BROKEN),
                seat(202, type(2, "VIP", 1, "10000"), 1, 0, SeatStatus.BROKEN),
                seat(203, type(3, "COUPLE", 2, "50000"), 2, 0, SeatStatus.BLOCKED));
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        patchStatus("OPEN_FOR_SALE", 409);
        assertEquals(ShowtimeStatus.SCHEDULED, showtime.getStatus());
    }

    @Test
    void scheduledShowCanOpenAndTerminalStatesCannotReopen() throws Exception {
        showtime.setStatus(ShowtimeStatus.SCHEDULED); patchStatus("OPEN_FOR_SALE", 200);
        assertEquals(ShowtimeStatus.OPEN_FOR_SALE, showtime.getStatus());
        patchStatus("CANCELLED", 200); assertEquals(ShowtimeStatus.CANCELLED, showtime.getStatus());
        patchStatus("OPEN_FOR_SALE", 409); patchStatus("CANCELLED", 200);
    }

    @Test
    void completionOnlyAllowedAfterEndAndCannotBeReopened() throws Exception {
        patchStatus("COMPLETED", 409);
        showtime.setStartTime(LocalDateTime.of(2029, 12, 31, 22, 0));
        showtime.setEndTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        patchStatus("COMPLETED", 200); assertEquals(ShowtimeStatus.COMPLETED, showtime.getStatus());
        patchStatus("OPEN_FOR_SALE", 409);
    }

    @Test
    void editOnlyScheduledAndProtectExistingTicketsAndActiveHolds() throws Exception {
        mvc.perform(put("/api/admin/showtimes/30").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isConflict());
        showtime.setStatus(ShowtimeStatus.SCHEDULED); when(tickets.existsByShowtimeId(30L)).thenReturn(true);
        mvc.perform(put("/api/admin/showtimes/30").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isConflict());
        when(tickets.existsByShowtimeId(30L)).thenReturn(false);
        when(holds.existsByShowtimeIdAndStatusAndExpiresAtAfter(eq(30L), eq(HoldStatus.HOLDING), any())).thenReturn(true);
        mvc.perform(put("/api/admin/showtimes/30").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isConflict());
        verify(showtimes, never()).save(any());
    }

    @Test
    void scheduledEditKeepsIdentityAndRecomputesEndTime() throws Exception {
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        mvc.perform(put("/api/admin/showtimes/30").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(30));
        assertEquals(LocalDateTime.of(2030, 1, 2, 19, 0), showtime.getEndTime());
        verify(showtimes).hasOverlap(20L, LocalDateTime.of(2030, 1, 2, 17, 0), LocalDateTime.of(2030, 1, 2, 19, 0), 30L);
    }

    @Test
    void editingMovieDurationDoesNotChangeExistingShowtimeUntilRescheduled() throws Exception {
        movie.setDurationMinutes(180);
        for (String path : List.of("/api/showtimes/30", "/api/admin/showtimes/30")) {
            var result = mvc.perform(get(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.durationMinutes").value(120))
                    .andReturn();
            var json = mapper.readTree(result.getResponse().getContentAsString());
            assertEquals(LocalDateTime.of(2030, 1, 2, 16, 0),
                    mapper.treeToValue(json.get("endTime"), LocalDateTime.class));
        }
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        mvc.perform(put("/api/admin/showtimes/30").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationMinutes").value(180));
        assertEquals(LocalDateTime.of(2030, 1, 2, 20, 0), showtime.getEndTime());
    }

    @Test
    void invalidInputsAndUnknownIdsReturnClientErrors() throws Exception {
        for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
                Map.entry("basePrice", -1), Map.entry("prices", List.of()),
                Map.entry("startTime", "not-a-date"),
                Map.entry("prices", List.of(Map.of("seatTypeId", 1, "price", -1))))) {
            Map<String, Object> request = body(); request.put(invalid.getKey(), invalid.getValue()); create(request, 400);
        }
        mvc.perform(get("/api/showtimes/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/showtimes").param("date", "not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/showtimes/30/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verify(showtimes, never()).save(any());
    }
}
