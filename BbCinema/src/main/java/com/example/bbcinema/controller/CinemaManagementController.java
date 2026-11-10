package com.example.bbcinema.controller;

import com.example.bbcinema.dto.CinemaDtos.*;
import com.example.bbcinema.service.CinemaManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Quản lý rạp, phòng và ghế")
public class CinemaManagementController {
    private final CinemaManagementService service;

    public CinemaManagementController(CinemaManagementService service) { this.service = service; }

    @GetMapping("/provinces")
    public List<ProvinceResponse> provinces() { return service.listProvinces(); }

    @PostMapping("/provinces")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvinceResponse createProvince(@Valid @RequestBody ProvinceRequest body) { return service.createProvince(body); }

    @GetMapping("/cinemas")
    public List<CinemaResponse> cinemas(@RequestParam(required = false) Integer provinceId) { return service.listCinemas(provinceId); }

    @GetMapping("/cinemas/{id}")
    public CinemaResponse cinema(@PathVariable Long id) { return service.getCinema(id); }

    @PostMapping("/cinemas")
    @ResponseStatus(HttpStatus.CREATED)
    public CinemaResponse createCinema(@Valid @RequestBody CinemaRequest body) { return service.createCinema(body); }

    @PutMapping("/cinemas/{id}")
    public CinemaResponse updateCinema(@PathVariable Long id, @Valid @RequestBody CinemaRequest body) { return service.updateCinema(id, body); }

    @GetMapping("/cinemas/{cinemaId}/auditoriums")
    public List<AuditoriumResponse> auditoriums(@PathVariable Long cinemaId) { return service.listAuditoriums(cinemaId); }

    @PostMapping("/cinemas/{cinemaId}/auditoriums")
    @ResponseStatus(HttpStatus.CREATED)
    public AuditoriumResponse createAuditorium(@PathVariable Long cinemaId, @Valid @RequestBody AuditoriumRequest body) {
        return service.createAuditorium(cinemaId, body);
    }

    @PatchMapping("/auditoriums/{id}/status")
    public AuditoriumResponse auditoriumStatus(@PathVariable Long id, @Valid @RequestBody AuditoriumStatusRequest body) {
        return service.updateAuditoriumStatus(id, body);
    }

    @GetMapping("/seat-types")
    public List<SeatTypeResponse> seatTypes() { return service.listSeatTypes(); }

    @PostMapping("/seat-types")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatTypeResponse createSeatType(@Valid @RequestBody SeatTypeRequest body) { return service.createSeatType(body); }

    @PostMapping("/auditoriums/{id}/seats/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatMapResponse generateSeats(@PathVariable Long id, @Valid @RequestBody GenerateSeatsRequest body) {
        return service.generateSeats(id, body);
    }

    @PutMapping("/auditoriums/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatMapResponse createLayout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest body) {
        return service.createLayout(id, body);
    }

    @GetMapping("/auditoriums/{id}/seat-map")
    public SeatMapResponse seatMap(@PathVariable Long id) { return service.getSeatMap(id); }

    @PatchMapping("/auditoriums/{id}/seats/{seatId}/status")
    public SeatResponse seatStatus(@PathVariable Long id, @PathVariable Long seatId, @Valid @RequestBody SeatStatusRequest body) {
        return service.updateSeatStatus(id, seatId, body);
    }
}
