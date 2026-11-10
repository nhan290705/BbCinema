package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.CinemaManagementController;
import com.example.bbcinema.enums.AuditoriumStatus;
import com.example.bbcinema.enums.CinemaStatus;
import com.example.bbcinema.enums.SeatStatus;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.CinemaManagementService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(CinemaManagementTest.Config.class)
@WebAppConfiguration
class CinemaManagementTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, CinemaManagementController.class,
            CinemaManagementService.class, ApiExceptionHandler.class})
    static class Config {
        @Bean ProvinceRepository provinces() { return mock(ProvinceRepository.class); }
        @Bean CinemaRepository cinemas() { return mock(CinemaRepository.class); }
        @Bean AuditoriumRepository auditoriums() { return mock(AuditoriumRepository.class); }
        @Bean SeatTypeRepository seatTypes() { return mock(SeatTypeRepository.class); }
        @Bean SeatRepository seats() { return mock(SeatRepository.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired ProvinceRepository provinces;
    @Autowired CinemaRepository cinemas;
    @Autowired AuditoriumRepository auditoriums;
    @Autowired SeatTypeRepository seatTypes;
    @Autowired SeatRepository seats;
    MockMvc mvc;
    Province province;
    Cinema cinema;
    Auditorium room;
    SeatType standard;
    SeatType vip;
    SeatType couple;
    List<Seat> savedSeats;

    @BeforeEach
    void setup() {
        reset(provinces, cinemas, auditoriums, seatTypes, seats);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
        province = new Province();
        province.setId(1); province.setProvinceName("Đà Nẵng"); province.setCode("DN");
        cinema = new Cinema();
        cinema.setId(10L); cinema.setProvince(province); cinema.setCinemaName("BBCinema");
        cinema.setAddress("123 Đường A"); cinema.setStatus(CinemaStatus.ACTIVE);
        room = new Auditorium();
        room.setId(20L); room.setCinema(cinema); room.setName("Phòng 1");
        room.setTotalRows(3); room.setTotalCols(4); room.setStatus(AuditoriumStatus.ACTIVE);
        standard = type(1, "STANDARD", 1);
        vip = type(2, "VIP", 1);
        couple = type(3, "COUPLE", 2);
        when(provinces.findById(1)).thenReturn(Optional.of(province));
        when(cinemas.findById(10L)).thenReturn(Optional.of(cinema));
        when(auditoriums.findById(20L)).thenReturn(Optional.of(room));
        when(auditoriums.findByIdForUpdate(20L)).thenReturn(Optional.of(room));
        when(seatTypes.findById(1)).thenReturn(Optional.of(standard));
        when(seatTypes.findById(2)).thenReturn(Optional.of(vip));
        when(seatTypes.findById(3)).thenReturn(Optional.of(couple));
        when(provinces.save(any(Province.class))).thenAnswer(inv -> {
            Province entity = inv.getArgument(0); entity.setId(2); return entity;
        });
        when(cinemas.save(any(Cinema.class))).thenAnswer(inv -> {
            Cinema entity = inv.getArgument(0); if (entity.getId() == null) entity.setId(11L); return entity;
        });
        when(auditoriums.save(any(Auditorium.class))).thenAnswer(inv -> {
            Auditorium entity = inv.getArgument(0); if (entity.getId() == null) entity.setId(21L); return entity;
        });
        when(seats.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));
        savedSeats = new ArrayList<>();
        when(seats.saveAll(any())).thenAnswer(inv -> {
            Iterable<Seat> input = inv.getArgument(0);
            for (Seat seat : input) {
                seat.setId(100L + savedSeats.size()); savedSeats.add(seat);
            }
            return new ArrayList<>(savedSeats);
        });
        when(seats.findByAuditoriumIdOrderByGridYAscGridXAsc(20L))
                .thenAnswer(inv -> new ArrayList<>(savedSeats));
    }

    private SeatType type(int id, String name, int span) {
        SeatType type = new SeatType(); type.setId(id); type.setTypeName(name);
        type.setSeatSpan(span); type.setDefaultSurcharge(BigDecimal.ZERO); return type;
    }

    @Test
    void managementRequiresAdminAndCsrfWhileAuthenticatedUsersCanView() throws Exception {
        mvc.perform(get("/api/auditoriums/20/seat-map")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoriums/20/seat-map").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auditoriumId").value(20));
        for (String role : new String[]{"CUSTOMER", "STAFF", "MANAGER"}) {
            mvc.perform(post("/api/provinces").with(user("account").roles(role)).with(csrf())
                    .contentType("application/json").content("{\"provinceName\":\"Đà Nẵng\",\"code\":\"DN\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/provinces").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"provinceName\":\"Đà Nẵng\",\"code\":\"DN\"}"))
                .andExpect(status().isForbidden());
        verify(provinces, never()).save(any());
    }

    @Test
    void createsProvinceAndRejectsDuplicateCode() throws Exception {
        String body = "{\"provinceName\":\"Đà Nẵng\",\"code\":\"DN\"}";
        mvc.perform(post("/api/provinces").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.provinceName").value("Đà Nẵng"));
        when(provinces.existsByCodeIgnoreCase("DN")).thenReturn(true);
        mvc.perform(post("/api/provinces").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        verify(provinces, times(1)).save(any());
    }

    @Test
    void createsCinemaWithProvinceAndEditsItWithoutChangingIdentity() throws Exception {
        String body = "{\"provinceId\":1,\"cinemaName\":\"Rạp mới\",\"address\":\"123 Đường B\",\"hotline\":\"0901234567\",\"latitude\":16.05,\"longitude\":108.2,\"status\":\"ACTIVE\"}";
        mvc.perform(post("/api/cinemas").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        verify(cinemas).save(argThat(c -> c.getProvince() == province && c.getCinemaName().equals("Rạp mới")
                && c.getLatitude().compareTo(new BigDecimal("16.05")) == 0));
        mvc.perform(put("/api/cinemas/10").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body.replace("ACTIVE", "MAINTENANCE")))
                .andExpect(status().isOk());
        assertEquals(10L, cinema.getId());
        assertEquals(CinemaStatus.MAINTENANCE, cinema.getStatus());
    }

    @Test
    void createsRoomForSelectedCinemaAndValidatesDimensions() throws Exception {
        String body = "{\"name\":\"Phòng 2\",\"totalRows\":3,\"totalCols\":4,\"status\":\"ACTIVE\"}";
        mvc.perform(post("/api/cinemas/10/auditoriums").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        verify(auditoriums).save(argThat(a -> a.getCinema() == cinema && a.getTotalRows() == 3 && a.getTotalCols() == 4));
        mvc.perform(post("/api/cinemas/10/auditoriums").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body.replace("\"totalRows\":3", "\"totalRows\":0")))
                .andExpect(status().isBadRequest());
        verify(auditoriums, times(1)).save(any());
    }

    @Test
    void generatesMixedRowsWithCorrectCoordinatesAndCoupleSpan() throws Exception {
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json")
                .content("{\"defaultSeatTypeId\":1,\"rowTypes\":{\"B\":2,\"C\":3}}"))
                .andExpect(status().is2xxSuccessful());
        assertEquals(10, savedSeats.size());
        Seat a1 = savedSeats.get(0); assertEquals("A", a1.getRowLabel());
        assertEquals(1, a1.getColNumber()); assertEquals(0, a1.getGridX()); assertEquals(0, a1.getGridY());
        assertSame(standard, a1.getSeatType()); assertEquals(SeatStatus.AVAILABLE, a1.getStatus());
        List<Seat> vipSeats = savedSeats.stream().filter(s -> s.getRowLabel().equals("B")).toList();
        assertEquals(4, vipSeats.size()); assertTrue(vipSeats.stream().allMatch(s -> s.getSeatType() == vip));
        List<Seat> coupleSeats = savedSeats.stream().filter(s -> s.getRowLabel().equals("C")).toList();
        assertEquals(2, coupleSeats.size());
        assertEquals(List.of(0, 2), coupleSeats.stream().map(Seat::getGridX).toList());
        assertEquals(List.of(1, 3), coupleSeats.stream().map(Seat::getColNumber).toList());
        mvc.perform(get("/api/auditoriums/20/seat-map").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cinemaId").value(10))
                .andExpect(jsonPath("$.totalRows").value(3)).andExpect(jsonPath("$.totalCols").value(4))
                .andExpect(jsonPath("$.seats.length()").value(10))
                .andExpect(jsonPath("$.seats[8].seatSpan").value(2))
                .andExpect(jsonPath("$.seats[9].gridX").value(2));
    }

    @Test
    void choosingProvinceThenCinemaReturnsOnlyTheirChildren() throws Exception {
        when(provinces.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(province));
        when(cinemas.findByProvinceIdOrderByCinemaNameAscIdAsc(1)).thenReturn(List.of(cinema));
        when(auditoriums.findByCinemaIdOrderByNameAscIdAsc(10L)).thenReturn(List.of(room));
        mvc.perform(get("/api/provinces").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/cinemas").param("provinceId", "1").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].provinceId").value(1));
        mvc.perform(get("/api/cinemas/10/auditoriums").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(20)).andExpect(jsonPath("$[0].cinemaId").value(10));
        verify(cinemas).findByProvinceIdOrderByCinemaNameAscIdAsc(1);
        verify(auditoriums).findByCinemaIdOrderByNameAscIdAsc(10L);
    }

    @Test
    void cannotGenerateOverExistingSeats() throws Exception {
        when(seats.existsByAuditoriumId(20L)).thenReturn(true);
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"defaultSeatTypeId\":1}"))
                .andExpect(status().isConflict());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void rowsBeyondZUseAaWithCorrectGridPosition() throws Exception {
        room.setTotalRows(27); room.setTotalCols(1);
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"defaultSeatTypeId\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seats[25].rowLabel").value("Z"))
                .andExpect(jsonPath("$.seats[26].rowLabel").value("AA"))
                .andExpect(jsonPath("$.seats[26].gridY").value(26));
    }

    @Test
    void unknownRowOverridesAreRejectedBeforeWriting() throws Exception {
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"defaultSeatTypeId\":1,\"rowTypes\":{\"AB\":2}}"))
                .andExpect(status().isBadRequest());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void missingSeatTypeAndOddCoupleRowDoNotProducePartialLayouts() throws Exception {
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"defaultSeatTypeId\":999}"))
                .andExpect(status().isNotFound());
        room.setTotalCols(3);
        mvc.perform(post("/api/auditoriums/20/seats/generate").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"defaultSeatTypeId\":1,\"rowTypes\":{\"C\":3}}"))
                .andExpect(status().isBadRequest());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void rejectsOutOfBoundsCustomSeatBeforeSaving() throws Exception {
        String body = "{\"seats\":[{\"rowLabel\":\"A\",\"colNumber\":4,\"gridX\":3,\"gridY\":0,\"seatTypeId\":3,\"status\":\"AVAILABLE\"}]}";
        mvc.perform(put("/api/auditoriums/20/seats").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void rejectsOverlappingCustomSeatsBeforeSavingAnyOfThem() throws Exception {
        String body = "{\"seats\":[{\"rowLabel\":\"A\",\"colNumber\":1,\"gridX\":0,\"gridY\":0,\"seatTypeId\":3,\"status\":\"AVAILABLE\"},{\"rowLabel\":\"A\",\"colNumber\":2,\"gridX\":1,\"gridY\":0,\"seatTypeId\":1,\"status\":\"AVAILABLE\"}]}";
        mvc.perform(put("/api/auditoriums/20/seats").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(seats, never()).saveAll(any());
    }

    @Test
    void customLayoutKeepsAislesAndPhysicalCoordinates() throws Exception {
        String body = "{\"seats\":[{\"rowLabel\":\"A\",\"colNumber\":1,\"gridX\":0,\"gridY\":0,\"seatTypeId\":1,\"status\":\"AVAILABLE\"},{\"rowLabel\":\"A\",\"colNumber\":3,\"gridX\":2,\"gridY\":0,\"seatTypeId\":1,\"status\":\"BROKEN\"}]}";
        mvc.perform(put("/api/auditoriums/20/seats").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().is2xxSuccessful());
        assertEquals(2, savedSeats.size());
        assertTrue(savedSeats.stream().noneMatch(s -> s.getGridX() == 1));
        mvc.perform(get("/api/auditoriums/20/seat-map").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats[1].gridX").value(2))
                .andExpect(jsonPath("$.seats[1].status").value("BROKEN"));
    }

    @Test
    void updatesRoomAndSeatStatusAndRejectsSeatFromAnotherRoom() throws Exception {
        Seat seat = new Seat(); seat.setId(100L); seat.setAuditorium(room); seat.setSeatType(standard);
        seat.setRowLabel("A"); seat.setColNumber(1); seat.setGridX(0); seat.setGridY(0); seat.setStatus(SeatStatus.AVAILABLE);
        when(seats.findByIdAndAuditoriumId(100L, 20L)).thenReturn(Optional.of(seat));
        mvc.perform(patch("/api/auditoriums/20/seats/100/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isOk());
        assertEquals(SeatStatus.BROKEN, seat.getStatus());
        mvc.perform(patch("/api/auditoriums/20/seats/100/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());
        assertEquals(SeatStatus.BLOCKED, seat.getStatus());
        mvc.perform(patch("/api/auditoriums/20/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk());
        assertEquals(AuditoriumStatus.MAINTENANCE, room.getStatus());
        mvc.perform(patch("/api/auditoriums/20/seats/999/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyAndUnknownResourcesReturnUsefulClientErrors() throws Exception {
        mvc.perform(post("/api/cinemas").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").exists());
        mvc.perform(get("/api/auditoriums/999/seat-map").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/cinemas/999").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/auditoriums/20/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }
}
