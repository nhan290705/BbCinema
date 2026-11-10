package com.example.bbcinema.service;

import com.example.bbcinema.dto.MovieDtos.*;
import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.model.movie.Genre;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.repository.GenreRepository;
import com.example.bbcinema.repository.MovieRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class MovieManagementService {
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final MoviePosterStorage posters;

    public MovieManagementService(MovieRepository movies, GenreRepository genres, MoviePosterStorage posters) {
        this.movies = movies;
        this.genres = genres;
        this.posters = posters;
    }

    public List<GenreResponse> listGenres() {
        return genres.findAll(Sort.by("genreName", "id")).stream().map(this::genreResponse).toList();
    }

    @Transactional
    public GenreResponse createGenre(GenreRequest body) {
        if (genres.existsByGenreNameIgnoreCase(body.genreName())) throw conflict("Tên thể loại đã tồn tại");
        Genre genre = new Genre();
        genre.setGenreName(body.genreName());
        return genreResponse(genres.save(genre));
    }

    @Transactional
    public GenreResponse updateGenre(Integer id, GenreRequest body) {
        Genre genre = genre(id);
        if (genres.existsByGenreNameIgnoreCaseAndIdNot(body.genreName(), id)) throw conflict("Tên thể loại đã tồn tại");
        genre.setGenreName(body.genreName());
        return genreResponse(genres.save(genre));
    }

    @Transactional
    public void deleteGenre(Integer id) {
        Genre genre = genre(id);
        if (movies.existsByGenresId(id)) throw conflict("Thể loại đang được gán cho phim. Hãy bỏ gán trước khi xóa");
        genres.delete(genre);
    }

    public MoviePage listMovies(String title, Integer genreId, MovieStatus status, int page, int size, boolean admin) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw bad("page phải từ 0 đến 100000, size phải từ 1 đến 100");
        }
        String search = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 200) throw bad("Tên phim tìm kiếm tối đa 200 ký tự");
        if (!admin && status == MovieStatus.HIDDEN) throw bad("Trạng thái HIDDEN chỉ dành cho quản trị viên");
        if (genreId != null) genre(genreId);
        Specification<Movie> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!admin) predicates.add(cb.notEqual(root.get("status"), MovieStatus.HIDDEN));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (!search.isEmpty()) {
                // Search literal text: '%' and '_' in movie titles must not act as wildcards.
                String escaped = search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'));
            }
            if (genreId != null) {
                predicates.add(cb.equal(root.join("genres").get("id"), genreId));
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = movies.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new MoviePage(result.getContent().stream().map(this::movieResponse).toList(),
                result.getTotalElements(), result.getTotalPages(), page, size);
    }

    public MovieResponse getMovie(Long id, boolean admin) {
        Movie movie = movie(id);
        if (!admin && movie.getStatus() == MovieStatus.HIDDEN) throw missing("Không tìm thấy phim");
        return movieResponse(movie);
    }

    @Transactional
    public MovieResponse createMovie(MovieRequest body) {
        return movieResponse(movies.save(apply(new Movie(), body)));
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest body) {
        return movieResponse(movies.save(apply(movie(id), body)));
    }

    @Transactional
    public MovieResponse createMovie(MovieRequest body, MultipartFile poster) {
        return saveWithPoster(apply(new Movie(), body), poster);
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest body, MultipartFile poster) {
        return saveWithPoster(apply(movie(id), body), poster);
    }

    private MovieResponse saveWithPoster(Movie movie, MultipartFile poster) {
        String created = poster == null ? null : posters.store(poster);
        if (created != null) {
            movie.setPosterUrl(created);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) posters.deleteCreated(created);
                    }
                });
            }
        }
        try { return movieResponse(movies.save(movie)); }
        catch (RuntimeException ex) { posters.deleteCreated(created); throw ex; }
    }

    @Transactional
    public MovieResponse updateStatus(Long id, MovieStatusRequest body) {
        Movie movie = movie(id);
        movie.setStatus(body.status());
        return movieResponse(movies.save(movie));
    }

    private Movie apply(Movie movie, MovieRequest body) {
        List<Genre> selected = genres.findAllById(body.genreIds());
        if (selected.size() != body.genreIds().size()) throw missing("Một hoặc nhiều thể loại không tồn tại");
        movie.setTitle(body.title());
        movie.setDescription(body.description());
        movie.setDurationMinutes(body.durationMinutes());
        movie.setAgeRating(body.ageRating());
        movie.setCastList(body.castList());
        movie.setDirector(body.director());
        movie.setPosterUrl(body.posterUrl());
        movie.setTrailerUrl(MovieTrailerSupport.normalize(body.trailerUrl()));
        movie.setReleaseDate(body.releaseDate());
        movie.setStatus(body.status());
        // Modify the managed collection so Hibernate maintains MOVIE_GENRES and preserves other films.
        movie.getGenres().clear();
        movie.getGenres().addAll(selected);
        return movie;
    }

    private Movie movie(Long id) { return movies.findById(id).orElseThrow(() -> missing("Không tìm thấy phim")); }
    private Genre genre(Integer id) { return genres.findById(id).orElseThrow(() -> missing("Không tìm thấy thể loại")); }
    private GenreResponse genreResponse(Genre genre) { return new GenreResponse(genre.getId(), genre.getGenreName()); }
    private MovieResponse movieResponse(Movie movie) {
        List<GenreResponse> selected = movie.getGenres().stream()
                .sorted(Comparator.comparing(Genre::getGenreName).thenComparing(Genre::getId))
                .map(this::genreResponse).toList();
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDescription(), movie.getDurationMinutes(),
                movie.getAgeRating(), movie.getCastList(), movie.getDirector(), movie.getPosterUrl(), movie.getTrailerUrl(),
                movie.getReleaseDate(), movie.getStatus(), selected);
    }

    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
