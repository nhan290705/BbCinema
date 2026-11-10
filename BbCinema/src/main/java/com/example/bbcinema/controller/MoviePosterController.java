package com.example.bbcinema.controller;

import com.example.bbcinema.service.MoviePosterStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/uploads/posters")
public class MoviePosterController {
    private final MoviePosterStorage storage;
    public MoviePosterController(MoviePosterStorage storage) { this.storage = storage; }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> poster(@PathVariable String filename) {
        Resource image = storage.load(filename);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(image);
    }
}
