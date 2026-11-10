package com.example.bbcinema.controller;

import com.example.bbcinema.dto.MovieDtos.*;
import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.service.MovieManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Quản lý phim và thể loại (Admin)")
public class MovieAdminController {
    private final MovieManagementService service;
    public MovieAdminController(MovieManagementService service) { this.service = service; }

    @PostMapping("/genres")
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse createGenre(@Valid @RequestBody GenreRequest body) { return service.createGenre(body); }

    @PutMapping("/genres/{id}")
    public GenreResponse updateGenre(@PathVariable Integer id, @Valid @RequestBody GenreRequest body) { return service.updateGenre(id, body); }

    @DeleteMapping("/genres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGenre(@PathVariable Integer id) { service.deleteGenre(id); }

    @GetMapping("/movies")
    public MoviePage movies(@RequestParam(required = false) String title,
                            @RequestParam(required = false) Integer genreId,
                            @RequestParam(required = false) MovieStatus status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "12") int size) {
        return service.listMovies(title, genreId, status, page, size, true);
    }

    @GetMapping("/movies/{id}")
    public MovieResponse movie(@PathVariable Long id) { return service.getMovie(id, true); }

    @PostMapping(value = "/movies", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest body) { return service.createMovie(body); }

    @PutMapping(value = "/movies/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest body) { return service.updateMovie(id, body); }

    @PostMapping(value = "/movies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovieWithPoster(@Valid @RequestPart("movie") MovieRequest body,
                                               @RequestPart(value = "poster", required = false) MultipartFile poster) {
        return service.createMovie(body, poster);
    }

    @PutMapping(value = "/movies/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MovieResponse updateMovieWithPoster(@PathVariable Long id, @Valid @RequestPart("movie") MovieRequest body,
                                               @RequestPart(value = "poster", required = false) MultipartFile poster) {
        return service.updateMovie(id, body, poster);
    }

    @PatchMapping("/movies/{id}/status")
    public MovieResponse updateStatus(@PathVariable Long id, @Valid @RequestBody MovieStatusRequest body) {
        return service.updateStatus(id, body);
    }
}
