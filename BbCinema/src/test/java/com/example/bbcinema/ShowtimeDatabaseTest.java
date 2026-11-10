package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.ShowtimeAdminController;
import com.example.bbcinema.controller.ShowtimeCatalogController;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.CustomUserDetailsService;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.ShowtimeManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in PostgreSQL checks. This isolated context validates schema and never runs seed runners. */
@EnabledIfEnvironmentVariable(named = "BBCINEMA_DATABASE_TEST", matches = "true")
@SpringBootTest(classes = ShowtimeDatabaseTest.DatabaseConfig.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "showtime-database-test", roles = "ADMIN")
class ShowtimeDatabaseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.example.bbcinema.model")
    @EnableJpaRepositories("com.example.bbcinema.repository")
    @Import({ShowtimeCatalogController.class, ShowtimeAdminController.class, ShowtimeManagementService.class,
            ApiExceptionHandler.class, SecurityConfig.class, PasswordConfig.class, CustomUserDetailsService.class})
    static class DatabaseConfig {
        @Bean Clock cinemaClock() {
            return Clock.fixed(Instant.parse("2040-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired ProvinceRepository provinces;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired SeatTypeRepository seatTypes;
    @Autowired SeatRepository seats;
    @Autowired MovieRepository movies;
    @Autowired ShowtimeRepository showtimes;

    private final List<Integer> provinceIds = new ArrayList<>(), typeIds = new ArrayList<>();
    private final List<Long> cinemaIds = new ArrayList<>(), roomIds = new ArrayList<>(), seatIds = new ArrayList<>(),
            movieIds = new ArrayList<>(), showtimeIds = new ArrayList<>();

    @Test
    void midnightOverlapIsRejectedWhileAdjacencyOtherRoomsAndCancelledSlotsAreAllowed() throws Exception {
        Fixture f = fixture();
        long first = createShowtime(f, f.roomId(), "2040-01-02T23:30:00");
        flushAndClear();
        var stored = showtimes.findById(first).orElseThrow();
        assertEquals("2040-01-03T01:30", stored.getEndTime().toString(), "End is derived from the 120-minute movie");
        mvc.perform(post("/api/admin/showtimes").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body(f, f.roomId(), "2040-01-03T00:30:00"))))
                .andExpect(status().isConflict());
        createShowtime(f, f.roomId(), "2040-01-03T01:30:00");
        createShowtime(f, f.secondRoomId(), "2040-01-03T00:30:00");
        patchStatus(first, "CANCELLED");
        long replacement = createShowtime(f, f.roomId(), "2040-01-02T23:30:00");
        patchStatus(replacement, "OPEN_FOR_SALE");
        flushAndClear();
        assertEquals(4, count("showtimes", "auditorium_id", List.of(f.roomId(), f.secondRoomId())));
        assertEquals(ShowtimeStatus.CANCELLED, showtimes.findById(first).orElseThrow().getStatus());
        assertEquals(1, readList(f, "2040-01-02", "0", "12").path("totalElements").asInt());
        assertEquals(0, readList(f, "2040-01-03", "0", "12").path("totalElements").asInt(),
                "A show crossing midnight belongs to its Vietnam-local start date");
    }

    @Test
    void publicFiltersAndPricedSeatMapUsePersistedFinalPricesWithoutAddingSurchargesOrDoublingCouples() throws Exception {
        Fixture f = fixture();
        long first = createShowtime(f, f.roomId(), "2040-01-03T09:00:00");
        long second = createShowtime(f, f.roomId(), "2040-01-03T12:00:00");
        long scheduled = createShowtime(f, f.roomId(), "2040-01-03T15:00:00");
        patchStatus(first, "OPEN_FOR_SALE");
        patchStatus(second, "OPEN_FOR_SALE");
        flushAndClear();

        JsonNode page = readList(f, "2040-01-03", "0", "1");
        assertEquals(2, page.path("totalElements").asInt());
        assertEquals(2, page.path("totalPages").asInt());
        assertEquals(1, page.path("items").size());
        assertEquals("OPEN_FOR_SALE", page.path("items").get(0).path("status").asText());
        JsonNode secondPage = readList(f, "2040-01-03", "1", "1");
        assertEquals(1, secondPage.path("items").size());
        assertNotEquals(page.path("items").get(0).path("id"), secondPage.path("items").get(0).path("id"));
        assertEquals(0, readList(f, "2040-01-04", "0", "12").path("totalElements").asInt());
        mvc.perform(get("/api/showtimes/" + scheduled).with(anonymous())).andExpect(status().isNotFound());

        JsonNode map = readJson("/api/showtimes/" + first + "/seat-map");
        assertEquals(6, map.path("seats").size());
        assertEquals(3, map.path("showtime").path("prices").size());
        JsonNode standard = seat(map, "A", 1), broken = seat(map, "A", 2), couple = seat(map, "A", 3);
        assertEquals(new BigDecimal("90000"), standard.path("price").decimalValue().stripTrailingZeros().setScale(0));
        assertTrue(standard.path("usable").asBoolean());
        assertEquals("BROKEN", broken.path("status").asText());
        assertFalse(broken.path("usable").asBoolean());
        assertEquals(2, couple.path("seatSpan").asInt());
        assertEquals(2, couple.path("gridX").asInt());
        assertEquals(0, couple.path("gridY").asInt());
        assertEquals(0, new BigDecimal("250000").compareTo(couple.path("price").decimalValue()),
                "A couple is one priced item, despite spanning two columns and having a nonzero default surcharge");
        assertTrue(couple.path("usable").asBoolean());
        assertTrue(seat(map, "B", 1).path("price").isNull());
        assertFalse(seat(map, "B", 1).path("usable").asBoolean());
        assertFalse(seat(map, "B", 3).path("usable").asBoolean());
        assertEquals(0, new BigDecimal("130000").compareTo(seat(map, "B", 2).path("price").decimalValue()));

        mvc.perform(get("/api/admin/showtimes").param("movieId", f.movieId().toString())
                .param("cinemaId", f.cinemaId().toString()).param("date", "2040-01-03"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void incompletePricingDoesNotPersistAndPublicQueriesRecheckMovieRoomAndCinemaEligibility() throws Exception {
        Fixture f = fixture();
        Map<String, Object> incomplete = body(f, f.roomId(), "2040-01-04T09:00:00");
        incomplete.put("prices", List.of(Map.of("seatTypeId", f.standardId(), "price", 90000)));
        mvc.perform(post("/api/admin/showtimes").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(incomplete))).andExpect(status().isBadRequest());
        flushAndClear();
        assertEquals(0, count("showtimes", "auditorium_id", List.of(f.roomId())));
        long id = createShowtime(f, f.roomId(), "2040-01-04T09:00:00");
        patchStatus(id, "OPEN_FOR_SALE");
        mvc.perform(patch("/api/admin/showtimes/" + id + "/status").with(csrf()).contentType("application/json")
                .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isConflict());

        movies.findById(f.movieId()).orElseThrow().setStatus(MovieStatus.HIDDEN);
        flushAndClear();
        assertPubliclyUnavailable(f, id);
        movies.findById(f.movieId()).orElseThrow().setStatus(MovieStatus.NOW_SHOWING);
        auditoriums.findById(f.roomId()).orElseThrow().setStatus(AuditoriumStatus.MAINTENANCE);
        flushAndClear();
        assertPubliclyUnavailable(f, id);
        auditoriums.findById(f.roomId()).orElseThrow().setStatus(AuditoriumStatus.ACTIVE);
        cinemas.findById(f.cinemaId()).orElseThrow().setStatus(CinemaStatus.ARCHIVED);
        flushAndClear();
        assertPubliclyUnavailable(f, id);
    }

    private void assertPubliclyUnavailable(Fixture f, long id) throws Exception {
        mvc.perform(get("/api/showtimes/" + id).with(anonymous())).andExpect(status().isNotFound());
        assertEquals(0, readList(f, "2040-01-04", "0", "12").path("totalElements").asInt());
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Province province = new Province(); province.setProvinceName("Showtime rollback " + suffix); province.setCode("ST" + suffix);
        province = provinces.save(province); provinceIds.add(province.getId());
        Cinema cinema = new Cinema(); cinema.setProvince(province); cinema.setCinemaName("Rạp " + suffix);
        cinema.setAddress("Địa chỉ kiểm thử rollback"); cinema.setStatus(CinemaStatus.ACTIVE);
        cinema = cinemas.save(cinema); cinemaIds.add(cinema.getId());
        SeatType standard = type("STANDARD_TEST_" + suffix, 1, "10000");
        SeatType vip = type("VIP_TEST_" + suffix, 1, "20000");
        SeatType couple = type("COUPLE_TEST_" + suffix, 2, "50000");
        Auditorium room = room(cinema, "Phòng 1", standard, vip, couple);
        Auditorium second = room(cinema, "Phòng 2", standard, vip, couple);
        Movie movie = new Movie(); movie.setTitle("Phim rollback " + suffix); movie.setDurationMinutes(120);
        movie.setAgeRating("T13"); movie.setStatus(MovieStatus.NOW_SHOWING);
        movie = movies.save(movie); movieIds.add(movie.getId());
        entityManager.flush();
        return new Fixture(movie.getId(), cinema.getId(), room.getId(), second.getId(), standard.getId(), vip.getId(), couple.getId());
    }

    private SeatType type(String name, int span, String surcharge) {
        SeatType type = new SeatType(); type.setTypeName(name); type.setSeatSpan(span); type.setDefaultSurcharge(new BigDecimal(surcharge));
        type = seatTypes.save(type); typeIds.add(type.getId()); return type;
    }

    private Auditorium room(Cinema cinema, String name, SeatType standard, SeatType vip, SeatType couple) {
        Auditorium room = new Auditorium(); room.setCinema(cinema); room.setName(name); room.setTotalRows(2); room.setTotalCols(4);
        room.setStatus(AuditoriumStatus.ACTIVE); room = auditoriums.save(room); roomIds.add(room.getId());
        seat(room, standard, "A", 0, 0, SeatStatus.AVAILABLE);
        seat(room, standard, "A", 1, 0, SeatStatus.BROKEN);
        seat(room, couple, "A", 2, 0, SeatStatus.AVAILABLE);
        seat(room, standard, "B", 0, 1, SeatStatus.AISLE);
        seat(room, vip, "B", 1, 1, SeatStatus.AVAILABLE);
        seat(room, couple, "B", 2, 1, SeatStatus.BLOCKED);
        return room;
    }

    private void seat(Auditorium room, SeatType type, String label, int x, int y, SeatStatus status) {
        Seat seat = new Seat(); seat.setAuditorium(room); seat.setSeatType(type); seat.setRowLabel(label);
        seat.setColNumber(x + 1); seat.setGridX(x); seat.setGridY(y); seat.setStatus(status);
        seatIds.add(seats.save(seat).getId());
    }

    private Map<String, Object> body(Fixture f, Long roomId, String startTime) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("movieId", f.movieId()); body.put("cinemaId", f.cinemaId()); body.put("auditoriumId", roomId);
        body.put("startTime", startTime); body.put("basePrice", 70000);
        body.put("prices", List.of(Map.of("seatTypeId", f.standardId(), "price", 90000),
                Map.of("seatTypeId", f.vipId(), "price", 130000), Map.of("seatTypeId", f.coupleId(), "price", 250000)));
        return body;
    }

    private long createShowtime(Fixture f, Long roomId, String startTime) throws Exception {
        String json = mvc.perform(post("/api/admin/showtimes").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body(f, roomId, startTime)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(json);
        long id = result.path("id").asLong(); assertTrue(id > 0); showtimeIds.add(id); return id;
    }

    private void patchStatus(long id, String value) throws Exception {
        mvc.perform(patch("/api/admin/showtimes/" + id + "/status").with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("status", value))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value));
    }

    private JsonNode readList(Fixture f, String date, String page, String size) throws Exception {
        String json = mvc.perform(get("/api/showtimes").with(anonymous())
                .param("movieId", f.movieId().toString()).param("cinemaId", f.cinemaId().toString())
                .param("date", date).param("page", page).param("size", size))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode readJson(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path).with(anonymous())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode seat(JsonNode map, String row, int col) {
        for (JsonNode seat : map.path("seats")) {
            if (row.equals(seat.path("rowLabel").asText()) && col == seat.path("colNumber").asInt()) return seat;
        }
        throw new AssertionError("Missing seat " + row + col);
    }

    private int count(String table, String field, List<Long> ids) {
        // Table/field identifiers are fixed internal test literals, never request input.
        return ((Number) entityManager.createNativeQuery("select count(*) from " + table + " where " + field + " in (:ids)")
                .setParameter("ids", ids).getSingleResult()).intValue();
    }
    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }

    @AfterTransaction
    void allTestOwnedRowsAndPricesWereRolledBack() {
        for (Long id : showtimeIds) {
            assertFalse(showtimes.existsById(id));
            assertEquals(0, count("showtime_prices", "showtime_id", List.of(id)));
        }
        for (Long id : movieIds) assertFalse(movies.existsById(id));
        for (Long id : seatIds) assertFalse(seats.existsById(id));
        for (Long id : roomIds) assertFalse(auditoriums.existsById(id));
        for (Long id : cinemaIds) assertFalse(cinemas.existsById(id));
        for (Integer id : typeIds) assertFalse(seatTypes.existsById(id));
        for (Integer id : provinceIds) assertFalse(provinces.existsById(id));
    }

    private record Fixture(Long movieId, Long cinemaId, Long roomId, Long secondRoomId,
                           Integer standardId, Integer vipId, Integer coupleId) {}
}
