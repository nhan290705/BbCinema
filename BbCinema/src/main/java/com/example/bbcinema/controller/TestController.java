package com.example.bbcinema.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Kiểm tra", description = "API kiểm tra hoạt động của BBCinema")
public class TestController {

    @GetMapping
    @Operation(summary = "Kiểm tra server")
    public String test() {
        return "BBCinema dang chay thanh cong!";
    }
}