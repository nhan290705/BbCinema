package com.example.bbcinema.controller;

import com.example.bbcinema.dto.AdminDtos.Overview;
import com.example.bbcinema.service.AdminOverviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Tổng quan quản trị (Admin)")
public class AdminOverviewController {
    private final AdminOverviewService service;
    public AdminOverviewController(AdminOverviewService service) { this.service = service; }

    @GetMapping("/api/admin/overview")
    public Overview overview() { return service.overview(); }
}
