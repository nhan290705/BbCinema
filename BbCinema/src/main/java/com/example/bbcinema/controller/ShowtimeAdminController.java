package com.example.bbcinema.controller;

import com.example.bbcinema.dto.ShowtimeDtos.*;
import com.example.bbcinema.enums.ShowtimeStatus;
import com.example.bbcinema.service.ShowtimeManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/showtimes")
@Tag(name = "Quản lý lịch chiếu và giá vé (Admin)")
public class ShowtimeAdminController {
    private final ShowtimeManagementService service;
    public ShowtimeAdminController(ShowtimeManagementService service) { this.service = service; }

    @GetMapping
    public ShowtimePage list(@RequestParam(required = false) Long movieId,
                              @RequestParam(required = false) Long cinemaId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(required = false) ShowtimeStatus status,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        return service.listShowtimes(movieId, cinemaId, date, status, page, size, true);
    }

    @GetMapping("/{id}")
    public ShowtimeResponse detail(@PathVariable Long id) { return service.getShowtime(id, true); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowtimeResponse create(@Valid @RequestBody ShowtimeRequest body) { return service.createShowtime(body); }

    @PutMapping("/{id}")
    public ShowtimeResponse update(@PathVariable Long id, @Valid @RequestBody ShowtimeRequest body) { return service.updateShowtime(id, body); }

    @PatchMapping("/{id}/status")
    public ShowtimeResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest body) { return service.updateStatus(id, body); }
}
