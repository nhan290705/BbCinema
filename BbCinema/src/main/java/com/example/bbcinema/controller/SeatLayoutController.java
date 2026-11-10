package com.example.bbcinema.controller;

import com.example.bbcinema.dto.SeatLayoutDtos.*;
import com.example.bbcinema.service.SeatLayoutService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auditoriums")
@Tag(name = "Thiết kế sơ đồ ghế (Admin)")
public class SeatLayoutController {
    private final SeatLayoutService service;
    public SeatLayoutController(SeatLayoutService service) { this.service = service; }

    @GetMapping("/{id}/layout")
    public LayoutResponse get(@PathVariable Long id) { return service.getLayout(id); }

    @PutMapping("/{id}/layout")
    public LayoutResponse save(@PathVariable Long id, @Valid @RequestBody SaveLayoutRequest body) { return service.saveLayout(id, body); }

    @PostMapping("/{id}/copy-layout")
    @ResponseStatus(HttpStatus.CREATED)
    public LayoutResponse copy(@PathVariable Long id, @Valid @RequestBody CopyLayoutRequest body) { return service.copyLayout(id, body); }
}
