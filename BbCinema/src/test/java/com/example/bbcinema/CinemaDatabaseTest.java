package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.CinemaManagementController;
import com.example.bbcinema.enums.AuditoriumStatus;
import com.example.bbcinema.enums.SeatStatus;
import com.example.bbcinema.model.cinema.SeatType;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.CustomUserDetailsService;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.CinemaManagementService;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Explicit opt-in verification against the PostgreSQL configured for the application.
 * Schema validation only; all test-owned rows are rolled back. No startup seed runners
 * are imported, so this test never creates accounts, roles, or default seat types.
 */
@EnabledIfEnvironmentVariable(named = "BBCINEMA_DATABASE_TEST", matches = "true")
@SpringBootTest(classes = CinemaDatabaseTest.DatabaseConfig.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "cinema-database-test", roles = "ADMIN")
class CinemaDatabaseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.example.bbcinema.model")
    @EnableJpaRepositories("com.example.bbcinema.repository")
    @Import({CinemaManagementController.class, CinemaManagementService.class, ApiExceptionHandler.class,
            SecurityConfig.class, PasswordConfig.class, CustomUserDetailsService.class})
    static class DatabaseConfig {}

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired ProvinceRepository provinces;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired SeatTypeRepository seatTypes;
    @Autowired SeatRepository seats;

    private final List<Integer> provinceIds = new ArrayList<>();
    private final List<Long> cinemaIds = new ArrayList<>();
    private final List<Long> roomIds = new ArrayList<>();
    private final List<Long> seatIds = new ArrayList<>();

    @Test
    void persistedHierarchyMixedSeatMapAndStatusChangesUseRealPostgres() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        SeatType standard = existingType("STANDARD");
        SeatType vip = existingType("VIP");
        SeatType couple = existingType("COUPLE");
        JsonNode generated = postJson("/api/auditoriums/" + hierarchy.roomId() + "/seats/generate", Map.of(
                "defaultSeatTypeId", standard.getId(),
                "rowTypes", Map.of("B", vip.getId(), "C", couple.getId())));
        assertEquals(10, generated.path("seats").size());
        for (JsonNode seat : generated.path("seats")) {
            long seatId = seat.path("id").asLong();
            assertTrue(seatId > 0, "Seat response must contain its real persisted identity");
            seatIds.add(seatId);
        }
        assertEquals("C", generated.path("seats").get(8).path("rowLabel").asText());
        assertEquals(2, generated.path("seats").get(8).path("seatSpan").asInt());
        assertEquals(2, generated.path("seats").get(9).path("gridX").asInt());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/cinemas").param("provinceId", Integer.toString(hierarchy.provinceId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(hierarchy.cinemaId().intValue()));
        mvc.perform(get("/api/cinemas/" + hierarchy.cinemaId() + "/auditoriums"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(hierarchy.roomId().intValue()));
        mvc.perform(get("/api/auditoriums/" + hierarchy.roomId() + "/seat-map"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats.length()").value(10))
                .andExpect(jsonPath("$.seats[0].gridX").value(0))
                .andExpect(jsonPath("$.seats[0].gridY").value(0))
                .andExpect(jsonPath("$.seats[4].seatTypeName").value("VIP"))
                .andExpect(jsonPath("$.seats[9].colNumber").value(3));

        long brokenSeatId = seatIds.get(0), blockedSeatId = seatIds.get(1);
        mvc.perform(patch("/api/auditoriums/" + hierarchy.roomId() + "/seats/" + brokenSeatId + "/status")
                .with(csrf()).contentType("application/json").content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value((int) brokenSeatId));
        mvc.perform(patch("/api/auditoriums/" + hierarchy.roomId() + "/seats/" + blockedSeatId + "/status")
                .with(csrf()).contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/auditoriums/" + hierarchy.roomId() + "/status")
                .with(csrf()).contentType("application/json").content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertEquals(SeatStatus.BROKEN, seats.findById(brokenSeatId).orElseThrow().getStatus());
        assertEquals(SeatStatus.BLOCKED, seats.findById(blockedSeatId).orElseThrow().getStatus());
        assertEquals(AuditoriumStatus.MAINTENANCE,
                auditoriums.findById(hierarchy.roomId()).orElseThrow().getStatus());
        mvc.perform(post("/api/auditoriums/" + hierarchy.roomId() + "/seats/generate")
                .with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("defaultSeatTypeId", standard.getId()))))
                .andExpect(status().isConflict());
        assertEquals(10, seats.findByAuditoriumIdOrderByGridYAscGridXAsc(hierarchy.roomId()).size());
    }

    @Test
    void invalidOverlappingLayoutWritesNoSeats() throws Exception {
        Hierarchy hierarchy = createHierarchy();
        SeatType standard = existingType("STANDARD");
        SeatType couple = existingType("COUPLE");
        String body = mapper.writeValueAsString(Map.of("seats", List.of(
                Map.of("rowLabel", "A", "colNumber", 1, "gridX", 0, "gridY", 0,
                        "seatTypeId", couple.getId(), "status", "AVAILABLE"),
                Map.of("rowLabel", "A", "colNumber", 2, "gridX", 1, "gridY", 0,
                        "seatTypeId", standard.getId(), "status", "AVAILABLE"))));
        mvc.perform(put("/api/auditoriums/" + hierarchy.roomId() + "/seats")
                .with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        entityManager.flush();
        assertFalse(seats.existsByAuditoriumId(hierarchy.roomId()));
    }

    private Hierarchy createHierarchy() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase();
        JsonNode province = postJson("/api/provinces", Map.of(
                "provinceName", "Kiểm thử rollback " + unique, "code", "T" + unique));
        int provinceId = province.path("id").asInt();
        provinceIds.add(provinceId);
        JsonNode cinema = postJson("/api/cinemas", Map.of(
                "provinceId", provinceId, "cinemaName", "Rạp kiểm thử " + unique,
                "address", "Địa chỉ kiểm thử tự động", "status", "ACTIVE"));
        long cinemaId = cinema.path("id").asLong();
        cinemaIds.add(cinemaId);
        JsonNode room = postJson("/api/cinemas/" + cinemaId + "/auditoriums", Map.of(
                "name", "Phòng kiểm thử", "totalRows", 3, "totalCols", 4, "status", "ACTIVE"));
        long roomId = room.path("id").asLong();
        roomIds.add(roomId);
        return new Hierarchy(provinceId, cinemaId, roomId);
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        String json = mvc.perform(post(path).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private SeatType existingType(String name) {
        return seatTypes.findByTypeNameIgnoreCase(name).orElseThrow(() ->
                new AssertionError("Thiếu loại ghế " + name + ". Chạy ứng dụng để khởi tạo loại ghế trước."));
    }

    @AfterTransaction
    void testOwnedRecordsWereRolledBack() {
        for (Long id : seatIds) assertFalse(seats.existsById(id), "Test seat must be rolled back");
        for (Long id : roomIds) assertFalse(auditoriums.existsById(id), "Test room must be rolled back");
        for (Long id : cinemaIds) assertFalse(cinemas.existsById(id), "Test cinema must be rolled back");
        for (Integer id : provinceIds) assertFalse(provinces.existsById(id), "Test province must be rolled back");
    }

    private record Hierarchy(Integer provinceId, Long cinemaId, Long roomId) {}
}
