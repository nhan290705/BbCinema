package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.SeatLayoutController;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.booking.Order;
import com.example.bbcinema.model.booking.SeatHold;
import com.example.bbcinema.model.booking.Ticket;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.model.movie.Showtime;
import com.example.bbcinema.model.user.Role;
import com.example.bbcinema.model.user.User;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.CustomUserDetailsService;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.SeatLayoutService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL checks; no sample-data runners are loaded and all fixture writes roll back. */
@EnabledIfEnvironmentVariable(named = "BBCINEMA_DATABASE_TEST", matches = "true")
@SpringBootTest(classes = SeatLayoutDatabaseTest.Config.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "bbcinema.open-browser=false"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "seat-layout-test", roles = "ADMIN")
class SeatLayoutDatabaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2040, 1, 1, 7, 0);

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.example.bbcinema.model")
    @EnableJpaRepositories("com.example.bbcinema.repository")
    @Import({SeatLayoutService.class, SeatLayoutController.class, ApiExceptionHandler.class,
            SecurityConfig.class, PasswordConfig.class, CustomUserDetailsService.class})
    static class Config {
        @Bean Clock cinemaClock() { return Clock.fixed(Instant.parse("2040-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired ProvinceRepository provinces;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository rooms;
    @Autowired SeatTypeRepository types;
    @Autowired SeatRepository seats;
    @Autowired MovieRepository movies;
    @Autowired ShowtimeRepository showtimes;
    @Autowired OrderRepository orders;
    @Autowired TicketRepository tickets;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired SeatHoldRepository holds;
    private final Map<String, Set<Long>> ownedIds = new LinkedHashMap<>();

    @Test
    void renumberingSwapsLabelsAtomicallyAndPersistsMixedDisplayWidthsWithoutChangingIdentity() throws Exception {
        Fixture fixture = fixture();
        JsonNode original = read(fixture.roomId());
        assertEquals(38, original.path("settings").path("seatWidth").asInt(), "Null legacy settings use shared defaults");
        ObjectNode body = body(original);
        input(body, fixture.a1()).put("colNumber", 2).put("seatTypeId", fixture.vipId()).put("widthUnits", 2);
        input(body, fixture.a2()).put("colNumber", 1);
        input(body, fixture.b1()).put("widthUnits", 3).put("rowLabel", "VIP2").put("colNumber", 11);
        ((ObjectNode) body.path("settings")).put("seatWidth", 64).put("seatHeight", 42)
                .put("columnGap", 12).put("rowGap", 16).put("screenPosition", "BOTTOM");
        body.put("totalCols", 10);
        ((ArrayNode) body.path("seats")).addObject().put("rowLabel", "C").put("colNumber", 7)
                .put("gridX", 6).put("gridY", 2).put("seatTypeId", fixture.standardId()).put("widthUnits", 2).put("status", "AVAILABLE");
        JsonNode saved = save(fixture.roomId(), body);
        trackLayout(saved);
        flushAndClear();
        Seat wideSingle = seats.findById(fixture.a1()).orElseThrow();
        assertEquals(2, wideSingle.getColNumber());
        assertEquals(2, wideSingle.getWidthUnits());
        assertEquals(1, wideSingle.getSeatType().getSeatSpan(), "A wider single remains a one-person price type");
        assertEquals(1, seats.findById(fixture.a2()).orElseThrow().getColNumber());
        assertEquals(3, seats.findById(fixture.b1()).orElseThrow().getWidthUnits());
        assertEquals("VIP2", seats.findById(fixture.b1()).orElseThrow().getRowLabel());
        Auditorium room = rooms.findById(fixture.roomId()).orElseThrow();
        assertEquals(64, room.getSeatWidth()); assertEquals(42, room.getSeatHeight());
        assertEquals(12, room.getColumnGap()); assertEquals(16, room.getRowGap());
        assertEquals("BOTTOM", room.getScreenPosition());
        assertEquals(5, seats.findByAuditoriumIdOrderByGridYAscGridXAsc(fixture.roomId()).size());
        assertEquals(0L, ((Number) em.createQuery("select count(s) from Seat s where s.auditorium.id = :room and s.rowLabel = '~'")
                .setParameter("room", fixture.roomId()).getSingleResult()).longValue(), "Temporary labels must never remain after save");
        mvc.perform(put(path(fixture.roomId())).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(body(original))))
                .andExpect(status().isConflict());
        assertEquals(saved.path("revision"), read(fixture.roomId()).path("revision"));
    }

    @Test
    void futureShowtimeKeepsTheOriginalLayoutSafeWhileCopyBecomesAnEditableNewRoom() throws Exception {
        Fixture fixture = fixture();
        createShowtime(fixture, ShowtimeStatus.SCHEDULED, NOW.plusDays(1));
        flushAndClear();
        JsonNode locked = read(fixture.roomId()); assertFalse(locked.path("structureEditable").asBoolean());
        ObjectNode geometry = body(locked); input(geometry, fixture.a1()).put("widthUnits", 2);
        mvc.perform(put(path(fixture.roomId())).with(csrf()).contentType("application/json").content(mapper.writeValueAsString(geometry)))
                .andExpect(status().isConflict());
        ObjectNode cosmetic = body(read(fixture.roomId()));
        ((ObjectNode) cosmetic.path("settings")).put("columnGap", 20);
        input(cosmetic, fixture.a1()).put("status", "BROKEN");
        save(fixture.roomId(), cosmetic);

        JsonNode copy = mapper.readTree(mvc.perform(post("/api/admin/auditoriums/" + fixture.roomId() + "/copy-layout")
                        .with(csrf()).contentType("application/json").content(mapper.writeValueAsString(
                                Map.of("cinemaId", fixture.cinemaId(), "name", "Phòng bản sao " + fixture.suffix()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        trackLayout(copy);
        assertNotEquals(fixture.roomId(), copy.path("auditoriumId").asLong());
        assertTrue(copy.path("structureEditable").asBoolean());
        Set<Long> oldIds = Set.of(fixture.a1(), fixture.a2(), fixture.b1(), fixture.c1());
        for (JsonNode item : copy.path("seats")) {
            assertFalse(oldIds.contains(item.path("id").asLong()));
            assertTrue(Set.of("AVAILABLE", "AISLE").contains(item.path("status").asText()));
        }
        ObjectNode editedCopy = body(copy);
        ((ObjectNode) editedCopy.path("seats").get(0)).put("widthUnits", 2);
        save(copy.path("auditoriumId").asLong(), editedCopy);
        flushAndClear();
        assertEquals(SeatStatus.BROKEN, seats.findById(fixture.a1()).orElseThrow().getStatus());
        assertEquals(1, Optional.ofNullable(seats.findById(fixture.a1()).orElseThrow().getWidthUnits()).orElse(1));
        assertEquals(20, rooms.findById(fixture.roomId()).orElseThrow().getColumnGap());
    }

    @Test
    void ticketHistoryAndExpiredHoldsAlsoPreventStructuralEditsAfterShowsFinish() throws Exception {
        Fixture ticketFixture = fixture();
        Showtime past = createShowtime(ticketFixture, ShowtimeStatus.COMPLETED, NOW.minusDays(1));
        Order order = new Order(); order.setOrderCode("LAYOUT-" + ticketFixture.suffix());
        order.setCinema(cinemas.findById(ticketFixture.cinemaId()).orElseThrow());
        order.setOrderChannel(OrderChannel.POS_COUNTER); order.setFinalAmount(new BigDecimal("90000"));
        order = orders.save(order); track("orders", order.getId());
        Ticket ticket = new Ticket(); ticket.setTicketCode("LAYOUT-" + ticketFixture.suffix());
        ticket.setQrCodeHash("layout-qr-" + ticketFixture.suffix()); ticket.setOrder(order); ticket.setShowtime(past);
        ticket.setSeat(seats.findById(ticketFixture.a1()).orElseThrow()); ticket.setPrice(new BigDecimal("90000"));
        ticket = tickets.save(ticket); track("tickets", ticket.getId());

        Fixture holdFixture = fixture();
        Showtime expiredShow = createShowtime(holdFixture, ShowtimeStatus.COMPLETED, NOW.minusDays(2));
        Role role = new Role(); role.setRoleName("LAYOUT_" + holdFixture.suffix());
        role = roles.save(role); track("roles", role.getId().longValue());
        User user = new User(); user.setRole(role); user.setFullName("Layout rollback fixture");
        user.setEmail("layout-" + holdFixture.suffix().toLowerCase(Locale.ROOT) + "@example.com");
        user.setPhone("SL" + holdFixture.suffix()); user.setPasswordHash("test-fixture-not-for-login");
        user.setStatus(UserStatus.ACTIVE); user.setCurrentPoints(0);
        user = users.save(user); track("users", user.getId());
        SeatHold hold = new SeatHold(); hold.setShowtime(expiredShow); hold.setSeat(seats.findById(holdFixture.a1()).orElseThrow());
        hold.setUser(user); hold.setHeldAt(NOW.minusDays(2)); hold.setExpiresAt(NOW.minusDays(2).plusMinutes(5));
        hold.setStatus(HoldStatus.EXPIRED_RELEASED); hold = holds.save(hold); track("seat_holds", hold.getId());
        flushAndClear();

        for (Fixture fixture : List.of(ticketFixture, holdFixture)) {
            JsonNode locked = read(fixture.roomId());
            assertFalse(locked.path("structureEditable").asBoolean());
            ObjectNode empty = body(locked); ((ArrayNode) empty.path("seats")).removeAll();
            mvc.perform(put(path(fixture.roomId())).with(csrf()).contentType("application/json")
                            .content(mapper.writeValueAsString(empty))).andExpect(status().isConflict());
            assertTrue(seats.existsById(fixture.a1()), "References to a historical seat must remain intact");
        }
        assertTrue(tickets.existsBySeatAuditoriumId(ticketFixture.roomId()));
        assertTrue(holds.existsBySeatAuditoriumId(holdFixture.roomId()));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        Province province = new Province(); province.setCode("SL" + suffix); province.setProvinceName("Layout rollback " + suffix);
        province = provinces.save(province); track("provinces", province.getId().longValue());
        Cinema cinema = new Cinema(); cinema.setProvince(province); cinema.setCinemaName("Layout cinema " + suffix);
        cinema.setAddress("Địa chỉ kiểm thử rollback"); cinema.setStatus(CinemaStatus.ACTIVE);
        cinema = cinemas.save(cinema); track("cinemas", cinema.getId());
        Auditorium room = new Auditorium(); room.setCinema(cinema); room.setName("Layout room " + suffix);
        room.setTotalRows(3); room.setTotalCols(8); room.setStatus(AuditoriumStatus.ACTIVE);
        room = rooms.save(room); track("auditoriums", room.getId());
        SeatType standard = type("SL_STD_" + suffix, 1), vip = type("SL_VIP_" + suffix, 1), couple = type("SL_DOUBLE_" + suffix, 2);
        Seat a1 = seat(room, standard, "A", 1, 0, 0, SeatStatus.AVAILABLE);
        Seat a2 = seat(room, vip, "A", 2, 2, 0, SeatStatus.BLOCKED);
        Seat b1 = seat(room, couple, "B", 1, 0, 1, SeatStatus.AVAILABLE);
        Seat c1 = seat(room, standard, "C", 1, 0, 2, SeatStatus.AISLE);
        em.flush();
        return new Fixture(suffix, cinema.getId(), room.getId(), standard.getId(), vip.getId(), couple.getId(),
                a1.getId(), a2.getId(), b1.getId(), c1.getId());
    }
    private SeatType type(String name, int span) {
        SeatType type = new SeatType(); type.setTypeName(name); type.setSeatSpan(span); type.setDefaultSurcharge(new BigDecimal("20000"));
        type = types.save(type); track("seat_types", type.getId().longValue()); return type;
    }
    private Seat seat(Auditorium room, SeatType type, String row, int number, int x, int y, SeatStatus status) {
        Seat seat = new Seat(); seat.setAuditorium(room); seat.setSeatType(type); seat.setRowLabel(row);
        seat.setColNumber(number); seat.setGridX(x); seat.setGridY(y); seat.setStatus(status);
        seat = seats.save(seat); track("seats", seat.getId()); return seat;
    }
    private Showtime createShowtime(Fixture fixture, ShowtimeStatus status, LocalDateTime start) {
        Movie movie = new Movie(); movie.setTitle("Layout movie " + fixture.suffix());
        movie.setDurationMinutes(120); movie.setAgeRating("T13"); movie.setStatus(MovieStatus.NOW_SHOWING);
        movie = movies.save(movie); track("movies", movie.getId());
        Showtime showtime = new Showtime(); showtime.setMovie(movie); showtime.setAuditorium(rooms.findById(fixture.roomId()).orElseThrow());
        showtime.setStartTime(start); showtime.setEndTime(start.plusHours(2)); showtime.setBasePrice(new BigDecimal("90000")); showtime.setStatus(status);
        showtime = showtimes.save(showtime); track("showtimes", showtime.getId()); return showtime;
    }
    private String path(long roomId) { return "/api/admin/auditoriums/" + roomId + "/layout"; }
    private JsonNode read(long roomId) throws Exception {
        return mapper.readTree(mvc.perform(get(path(roomId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ObjectNode body(JsonNode layout) {
        ObjectNode body = mapper.createObjectNode();
        for (String key : List.of("revision", "totalRows", "totalCols", "settings")) body.set(key, layout.path(key).deepCopy());
        ArrayNode selected = body.putArray("seats");
        for (JsonNode seat : layout.path("seats")) {
            ObjectNode item = selected.addObject();
            for (String key : List.of("id", "rowLabel", "colNumber", "gridX", "gridY", "seatTypeId", "widthUnits", "status")) item.set(key, seat.path(key).deepCopy());
        }
        return body;
    }
    private ObjectNode input(ObjectNode body, long id) {
        for (JsonNode seat : body.path("seats")) if (seat.path("id").asLong() == id) return (ObjectNode) seat;
        throw new AssertionError("Missing seat " + id);
    }
    private JsonNode save(long roomId, ObjectNode body) throws Exception {
        return mapper.readTree(mvc.perform(put(path(roomId)).with(csrf()).contentType("application/json")
                        .content(mapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void trackLayout(JsonNode layout) {
        track("auditoriums", layout.path("auditoriumId").asLong());
        for (JsonNode seat : layout.path("seats")) track("seats", seat.path("id").asLong());
    }
    private void track(String table, long id) { ownedIds.computeIfAbsent(table, ignored -> new HashSet<>()).add(id); }
    private void flushAndClear() { em.flush(); em.clear(); }

    @AfterTransaction
    void allFixtureRowsWereRolledBack() {
        for (var entry : ownedIds.entrySet()) {
            assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from " + entry.getKey() + " where id in (:ids)")
                    .setParameter("ids", entry.getValue()).getSingleResult()).longValue(), entry.getKey());
        }
    }

    private record Fixture(String suffix, Long cinemaId, Long roomId, Integer standardId, Integer vipId, Integer coupleId,
                           Long a1, Long a2, Long b1, Long c1) {}
}
