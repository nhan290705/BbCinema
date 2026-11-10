package com.example.bbcinema.controller;

import com.example.bbcinema.dto.ShowtimeDtos.*;
import com.example.bbcinema.enums.ShowtimeStatus;
import com.example.bbcinema.service.ShowtimeManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
@Tag(name = "Lịch chiếu và giá vé cho khách hàng")
public class ShowtimeCatalogController {
    private final ShowtimeManagementService service;
    public ShowtimeCatalogController(ShowtimeManagementService service) { this.service = service; }

    @GetMapping
    public ShowtimePage list(@RequestParam(required = false) Long movieId,
                              @RequestParam(required = false) Long cinemaId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(required = false) ShowtimeStatus status,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        return service.listShowtimes(movieId, cinemaId, date, status, page, size, false);
    }

    @GetMapping("/cinemas")
    public List<CinemaOption> cinemas() { return service.listPublicCinemas(); }

    @GetMapping("/{id}")
    public ShowtimeResponse detail(@PathVariable Long id) { return service.getShowtime(id, false); }

    @GetMapping("/{id}/seat-map")
    public PricedSeatMap seatMap(@PathVariable Long id) { return service.getSeatMap(id); }
}
