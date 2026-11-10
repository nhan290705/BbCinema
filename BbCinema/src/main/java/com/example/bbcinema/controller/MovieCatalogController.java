package com.example.bbcinema.controller;

import com.example.bbcinema.dto.MovieDtos.*;
import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.service.MovieManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Danh sách phim cho khách hàng")
public class MovieCatalogController {
    private final MovieManagementService service;
    public MovieCatalogController(MovieManagementService service) { this.service = service; }

    @GetMapping("/genres")
    public List<GenreResponse> genres() { return service.listGenres(); }

    @GetMapping("/movies")
    public MoviePage movies(@RequestParam(required = false) String title,
                            @RequestParam(required = false) Integer genreId,
                            @RequestParam(required = false) MovieStatus status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "12") int size) {
        return service.listMovies(title, genreId, status, page, size, false);
    }

    @GetMapping("/movies/{id}")
    public MovieResponse movie(@PathVariable Long id) { return service.getMovie(id, false); }
}
