package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.SeatLayoutController;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.SeatLayoutService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(SeatLayoutTest.Config.class)
@WebAppConfiguration
class SeatLayoutTest {
    private static final String PATH = "/api/admin/auditoriums/20/layout";
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 7, 0);

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, SeatLayoutController.class,
            SeatLayoutService.class, ApiExceptionHandler.class})
    static class Config {
        @Bean AuditoriumRepository auditoriums() { return mock(AuditoriumRepository.class); }
        @Bean CinemaRepository cinemas() { return mock(CinemaRepository.class); }
        @Bean SeatRepository seats() { return mock(SeatRepository.class); }
        @Bean SeatTypeRepository types() { return mock(SeatTypeRepository.class); }
        @Bean ShowtimeRepository showtimes() { return mock(ShowtimeRepository.class); }
        @Bean TicketRepository tickets() { return mock(TicketRepository.class); }
        @Bean SeatHoldRepository holds() { return mock(SeatHoldRepository.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired CinemaRepository cinemas;
    @Autowired SeatRepository seats;
    @Autowired SeatTypeRepository types;
    @Autowired ShowtimeRepository showtimes;
    @Autowired TicketRepository tickets;
    @Autowired SeatHoldRepository holds;
    @Autowired ObjectMapper mapper;
    MockMvc mvc;
    Auditorium room;
    Cinema cinema;
    SeatType standard, vip, couple;
    final Map<Long, Auditorium> rooms = new HashMap<>();
    final List<Seat> stored = new ArrayList<>();

    @BeforeEach
    void setup() {
        reset(auditoriums, cinemas, seats, types, showtimes, tickets, holds);
        rooms.clear(); stored.clear();
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
        Province province = new Province(); province.setId(1); province.setProvinceName("Đà Nẵng"); province.setCode("DN");
        cinema = new Cinema(); cinema.setId(10L); cinema.setProvince(province); cinema.setCinemaName("Rạp kiểm thử");
        cinema.setAddress("Địa chỉ mẫu"); cinema.setStatus(CinemaStatus.ACTIVE);
        room = new Auditorium(); room.setId(20L); room.setCinema(cinema); room.setName("Phòng 1");
        room.setTotalRows(3); room.setTotalCols(8); room.setStatus(AuditoriumStatus.ACTIVE);
        room.setSeatWidth(null); room.setSeatHeight(null); room.setColumnGap(null); room.setRowGap(null); room.setScreenPosition(null);
        rooms.put(20L, room);
        standard = type(1, "STANDARD", 1); vip = type(2, "VIP", 1); couple = type(3, "COUPLE", 2);
        stored.add(seat(100L, standard, "A", 1, 0, 0, SeatStatus.AVAILABLE));
        stored.add(seat(101L, vip, "A", 2, 2, 0, SeatStatus.BLOCKED));
        stored.add(seat(102L, couple, "B", 1, 1, 1, SeatStatus.AVAILABLE));
        stored.add(seat(103L, standard, "C", 1, 0, 2, SeatStatus.AISLE));
        when(auditoriums.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(rooms.get(inv.getArgument(0))));
        when(auditoriums.findByIdForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(rooms.get(inv.getArgument(0))));
        when(cinemas.findById(10L)).thenReturn(Optional.of(cinema));
        for (SeatType type : List.of(standard, vip, couple)) when(types.findById(type.getId())).thenReturn(Optional.of(type));
        when(types.findAllById(any())).thenAnswer(inv -> {
            List<SeatType> result = new ArrayList<>();
            for (Integer id : (Iterable<Integer>) inv.getArgument(0)) {
                for (SeatType type : List.of(standard, vip, couple)) if (id.equals(type.getId())) result.add(type);
            }
            return result;
        });
        when(seats.findByAuditoriumIdOrderByGridYAscGridXAsc(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return stored.stream().filter(s -> id.equals(s.getAuditorium().getId()))
                    .sorted(Comparator.comparing(Seat::getGridY).thenComparing(Seat::getGridX)).toList();
        });
        AtomicLong nextRoomId = new AtomicLong(30), nextSeatId = new AtomicLong(200);
        when(auditoriums.save(any(Auditorium.class))).thenAnswer(inv -> {
            Auditorium saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(nextRoomId.getAndIncrement());
            rooms.put(saved.getId(), saved); return saved;
        });
        when(seats.saveAll(any())).thenAnswer(inv -> {
            List<Seat> result = new ArrayList<>();
            for (Seat saved : (Iterable<Seat>) inv.getArgument(0)) {
                if (saved.getId() == null) saved.setId(nextSeatId.getAndIncrement());
                stored.removeIf(previous -> previous.getId().equals(saved.getId()));
                stored.add(saved); result.add(saved);
            }
            return result;
        });
        doAnswer(inv -> {
            for (Seat removed : (Iterable<Seat>) inv.getArgument(0)) stored.removeIf(s -> s.getId().equals(removed.getId()));
            return null;
        }).when(seats).deleteAll(any(Iterable.class));
    }

    private SeatType type(int id, String name, int span) {
        SeatType type = new SeatType(); type.setId(id); type.setTypeName(name);
        type.setSeatSpan(span); type.setDefaultSurcharge(BigDecimal.ZERO); return type;
    }
    private Seat seat(long id, SeatType type, String label, int number, int x, int y, SeatStatus status) {
        Seat seat = new Seat(); seat.setId(id); seat.setAuditorium(room); seat.setSeatType(type);
        seat.setRowLabel(label); seat.setColNumber(number); seat.setGridX(x); seat.setGridY(y);
        seat.setStatus(status); seat.setWidthUnits(null); return seat;
    }
    private JsonNode read() throws Exception {
        return mapper.readTree(mvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ObjectNode body(JsonNode layout) {
        ObjectNode body = mapper.createObjectNode();
        for (String key : List.of("revision", "totalRows", "totalCols", "settings")) body.set(key, layout.path(key).deepCopy());
        ArrayNode selected = body.putArray("seats");
        for (JsonNode seat : layout.path("seats")) {
            ObjectNode item = selected.addObject();
            for (String key : List.of("id", "rowLabel", "colNumber", "gridX", "gridY", "seatTypeId", "widthUnits", "status")) {
                item.set(key, seat.path(key).deepCopy());
            }
        }
        return body;
    }
    private ObjectNode input(ObjectNode body, long id) {
        for (JsonNode seat : body.path("seats")) if (seat.path("id").asLong() == id) return (ObjectNode) seat;
        throw new AssertionError("Missing seat " + id);
    }
    private JsonNode save(ObjectNode body) throws Exception {
        return mapper.readTree(mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void editorRequiresAdminAndEverySaveRequiresCsrf() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "MANAGER")) {
            mvc.perform(get(PATH).with(user("account").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditoriums, seats, showtimes, tickets, holds);
    }

    @Test
    void legacyLayoutsExposeStableRevisionAndFallbackDimensions() throws Exception {
        JsonNode layout = read();
        assertTrue(layout.path("structureEditable").asBoolean());
        assertTrue(layout.path("revision").asText().matches("[a-fA-F0-9]{64}"));
        assertEquals(layout.path("revision"), read().path("revision"));
        assertEquals(38, layout.path("settings").path("seatWidth").asInt());
        assertEquals(36, layout.path("settings").path("seatHeight").asInt());
        assertEquals(7, layout.path("settings").path("columnGap").asInt());
        assertEquals(7, layout.path("settings").path("rowGap").asInt());
        assertEquals("TOP", layout.path("settings").path("screenPosition").asText());
        assertEquals(1, layout.path("seats").get(0).path("widthUnits").asInt());
        assertEquals(2, layout.path("seats").get(2).path("widthUnits").asInt());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void savesMixedTypesCustomLabelsAndWidthsWhileRetainingExistingSeatIds() throws Exception {
        JsonNode original = read(); ObjectNode request = body(original);
        request.put("totalRows", 4); request.put("totalCols", 10);
        input(request, 100).put("seatTypeId", 2).put("widthUnits", 2).put("rowLabel", "R1");
        input(request, 101).put("rowLabel", "R1");
        input(request, 102).put("widthUnits", 3);
        ((ObjectNode) request.path("settings")).put("seatWidth", 60).put("rowGap", 20).put("screenPosition", "BOTTOM");
        ((ArrayNode) request.path("seats")).addObject().put("rowLabel", "D").put("colNumber", 9)
                .put("gridX", 7).put("gridY", 3).put("seatTypeId", 1).put("widthUnits", 2).put("status", "AVAILABLE");
        JsonNode result = save(request);
        assertEquals(5, result.path("seats").size());
        assertNotEquals(original.path("revision"), result.path("revision"));
        assertEquals(60, result.path("settings").path("seatWidth").asInt());
        assertEquals("BOTTOM", result.path("settings").path("screenPosition").asText());
        for (long id : List.of(100L, 101L, 102L, 103L)) assertTrue(stored.stream().anyMatch(s -> s.getId() == id));
        Seat wideSingle = stored.stream().filter(s -> s.getId() == 100L).findFirst().orElseThrow();
        assertEquals(2, wideSingle.getWidthUnits());
        assertEquals(1, wideSingle.getSeatType().getSeatSpan(), "Display width must not turn a single into a couple-priced item");
        assertEquals("R1", wideSingle.getRowLabel());
        verify(auditoriums).findByIdForUpdate(20L);
    }

    @Test
    void staleRevisionRejectsSavingBeforeAnyMutation() throws Exception {
        ObjectNode request = body(read());
        stored.get(0).setStatus(SeatStatus.BROKEN);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(request))).andExpect(status().isConflict());
        verify(seats, never()).saveAll(any()); verify(seats, never()).deleteAll(any(Iterable.class));
        verify(auditoriums, never()).save(any());
    }

    @Test
    void rejectsOverlapsOutOfBoundsAndCouplesNarrowerThanTheirType() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            ObjectNode request = body(read());
            if (scenario == 0) input(request, 100).put("widthUnits", 4);
            if (scenario == 1) input(request, 102).put("gridX", 7).put("widthUnits", 2);
            if (scenario == 2) input(request, 102).put("widthUnits", 1);
            mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                    .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        verify(seats, never()).saveAll(any()); verify(seats, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void rejectsForeignIdsDuplicateIdsAndInconsistentRowLabels() throws Exception {
        ObjectNode foreign = body(read()); input(foreign, 100).put("id", 9999);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(foreign)))
                .andExpect(result -> assertTrue(Set.of(400, 404).contains(result.getResponse().getStatus())));
        ObjectNode duplicate = body(read()); input(duplicate, 101).put("id", 100);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(duplicate))).andExpect(status().isBadRequest());
        ObjectNode labels = body(read()); input(labels, 101).put("rowLabel", "OTHER");
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(labels))).andExpect(status().isBadRequest());
        verify(seats, never()).saveAll(any()); verify(seats, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void futureShowtimesBlockStructuralChangesButPermitPhysicalStatusAndDisplaySettings() throws Exception {
        when(showtimes.existsByAuditoriumIdAndStatusInAndEndTimeAfter(eq(20L), anyCollection(), eq(NOW))).thenReturn(true);
        JsonNode locked = read(); assertFalse(locked.path("structureEditable").asBoolean());
        assertFalse(locked.path("lockReason").asText().isBlank());
        ObjectNode request = body(locked);
        ((ObjectNode) request.path("settings")).put("columnGap", 18);
        input(request, 100).put("status", "BROKEN");
        JsonNode saved = save(request);
        assertEquals(18, saved.path("settings").path("columnGap").asInt());
        assertEquals(SeatStatus.BROKEN, stored.stream().filter(s -> s.getId() == 100L).findFirst().orElseThrow().getStatus());
        ObjectNode structural = body(saved); input(structural, 100).put("widthUnits", 2);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(structural))).andExpect(status().isConflict());
    }

    @Test
    void historicalTicketsAndHoldsProtectTheLayoutAndAisleConversions() throws Exception {
        when(tickets.existsBySeatAuditoriumId(20L)).thenReturn(true);
        ObjectNode ticketBody = body(read()); input(ticketBody, 100).put("status", "AISLE");
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(ticketBody))).andExpect(status().isConflict());
        when(tickets.existsBySeatAuditoriumId(20L)).thenReturn(false);
        when(holds.existsBySeatAuditoriumId(20L)).thenReturn(true);
        ObjectNode holdBody = body(read()); ((ArrayNode) holdBody.path("seats")).removeAll();
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(holdBody))).andExpect(status().isConflict());
        verify(seats, never()).deleteAll(any(Iterable.class));
    }

    @Test
    void cloningALockedRoomMakesANewEditableLayoutWithoutReusingSeatIds() throws Exception {
        when(tickets.existsBySeatAuditoriumId(20L)).thenReturn(true);
        JsonNode copy = mapper.readTree(mvc.perform(post("/api/admin/auditoriums/20/copy-layout")
                        .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                        .content("{\"cinemaId\":10,\"name\":\"Phòng thiết kế mới\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertNotEquals(20, copy.path("auditoriumId").asLong());
        assertTrue(copy.path("structureEditable").asBoolean());
        assertEquals(4, copy.path("seats").size());
        for (JsonNode seat : copy.path("seats")) {
            assertTrue(seat.path("id").asLong() >= 200);
            assertTrue(Set.of("AVAILABLE", "AISLE").contains(seat.path("status").asText()));
        }
        assertEquals(SeatStatus.BLOCKED, stored.stream().filter(s -> s.getId() == 101L).findFirst().orElseThrow().getStatus());
    }

    @Test
    void validatesDimensionsAndSettingsAndCanClearAnUnusedRoom() throws Exception {
        ObjectNode invalid = body(read()); ((ObjectNode) invalid.path("settings")).put("seatWidth", 23);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        ObjectNode tooLarge = body(read()); tooLarge.put("totalRows", 100).put("totalCols", 100);
        mvc.perform(put(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(tooLarge))).andExpect(status().isBadRequest());
        ObjectNode empty = body(read()); ((ArrayNode) empty.path("seats")).removeAll();
        JsonNode cleared = save(empty); assertEquals(0, cleared.path("seats").size());
        assertTrue(stored.isEmpty());
    }
}
