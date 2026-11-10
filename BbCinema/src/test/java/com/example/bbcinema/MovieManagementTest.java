package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.MovieAdminController;
import com.example.bbcinema.controller.MovieCatalogController;
import com.example.bbcinema.controller.MoviePosterController;
import com.example.bbcinema.enums.MovieStatus;
import com.example.bbcinema.model.movie.Genre;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.repository.GenreRepository;
import com.example.bbcinema.repository.MovieRepository;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.MovieManagementService;
import com.example.bbcinema.service.MoviePosterStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(MovieManagementTest.Config.class)
@WebAppConfiguration
class MovieManagementTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PasswordConfig.class, MovieCatalogController.class,
            MovieAdminController.class, MoviePosterController.class, MovieManagementService.class, ApiExceptionHandler.class})
    static class Config {
        static Path posterDirectory;
        @Bean MoviePosterStorage posters() throws IOException {
            posterDirectory = Files.createTempDirectory("bbcinema-movie-api-");
            return new MoviePosterStorage(posterDirectory.toString());
        }
        @Bean MovieRepository movies() { return mock(MovieRepository.class); }
        @Bean GenreRepository genres() { return mock(GenreRepository.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired MovieRepository movies;
    @Autowired GenreRepository genres;
    @Autowired ObjectMapper mapper;
    @Autowired MoviePosterStorage posters;
    private final List<String> createdPosters = new ArrayList<>();
    MockMvc mvc;
    Genre action;
    Genre comedy;
    Movie movie;

    @AfterEach
    void removeTestPosters() { createdPosters.forEach(posters::deleteCreated); }

    @AfterAll
    static void removeEmptyTestDirectory() throws IOException { if (Config.posterDirectory != null) Files.deleteIfExists(Config.posterDirectory); }

    @BeforeEach
    void setup() {
        reset(movies, genres);
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
        action = genre(1, "Hành động");
        comedy = genre(2, "Hài");
        movie = new Movie();
        movie.setId(10L); movie.setTitle("Chuyến đi cuối tuần");
        movie.setDescription("Nội dung phim"); movie.setDurationMinutes(110); movie.setAgeRating("T13");
        movie.setCastList("Diễn viên A, Diễn viên B"); movie.setDirector("Đạo diễn A");
        movie.setPosterUrl("https://example.com/poster.jpg");
        movie.setTrailerUrl("https://example.com/trailer");
        movie.setReleaseDate(LocalDate.of(2027, 1, 1)); movie.setStatus(MovieStatus.NOW_SHOWING);
        movie.setGenres(new HashSet<>(Set.of(action, comedy)));
        when(movies.findById(10L)).thenReturn(Optional.of(movie));
        when(genres.findById(1)).thenReturn(Optional.of(action));
        when(genres.findById(2)).thenReturn(Optional.of(comedy));
        when(genres.findAllById(any())).thenAnswer(inv -> {
            List<Genre> result = new ArrayList<>();
            for (Integer id : (Iterable<Integer>) inv.getArgument(0)) {
                if (id == 1) result.add(action);
                if (id == 2) result.add(comedy);
            }
            return result;
        });
        when(genres.findAll(any(Sort.class))).thenReturn(List.of(action, comedy));
        when(genres.save(any(Genre.class))).thenAnswer(inv -> {
            Genre entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId(3);
            return entity;
        });
        when(movies.save(any(Movie.class))).thenAnswer(inv -> {
            Movie entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId(11L);
            return entity;
        });
        when(movies.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(movie), inv.getArgument(1), 1));
    }

    private Genre genre(int id, String name) {
        Genre result = new Genre(); result.setId(id); result.setGenreName(name); return result;
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Phim mới"); body.put("description", "Nội dung đầy đủ");
        body.put("durationMinutes", 120); body.put("ageRating", "T16");
        body.put("castList", "Diễn viên A"); body.put("director", "Đạo diễn B");
        body.put("posterUrl", "https://example.com/new-poster.jpg");
        body.put("trailerUrl", "https://example.com/new-trailer");
        body.put("releaseDate", "2027-02-01"); body.put("status", "COMING_SOON");
        body.put("genreIds", List.of(1, 2));
        return body;
    }

    @Test
    void guestsCanViewCatalogGenresAndCompleteMovieDetail() throws Exception {
        mvc.perform(get("/api/movies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/api/genres")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/movies/10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Nội dung phim"))
                .andExpect(jsonPath("$.durationMinutes").value(110))
                .andExpect(jsonPath("$.ageRating").value("T13"))
                .andExpect(jsonPath("$.posterUrl").value("https://example.com/poster.jpg"))
                .andExpect(jsonPath("$.trailerUrl").value("https://example.com/trailer"))
                .andExpect(jsonPath("$.genres.length()").value(2));
    }

    @Test
    void hiddenMovieCannotBeReadByCustomerButAdminCanReadIt() throws Exception {
        movie.setStatus(MovieStatus.HIDDEN);
        mvc.perform(get("/api/movies/10")).andExpect(status().isNotFound());
        mvc.perform(get("/api/movies/10").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/movies/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));
        mvc.perform(get("/api/movies").param("status", "HIDDEN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanListHiddenFilmsAndPaginationIsBounded() throws Exception {
        mvc.perform(get("/api/admin/movies").param("status", "HIDDEN").param("page", "2").param("size", "5")
                .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));
        verify(movies).findAll(any(Specification.class), argThat((Pageable p) -> p.getPageNumber() == 2 && p.getPageSize() == 5));
        mvc.perform(get("/api/movies").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/movies").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/movies").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void managementRequiresAdminAndCsrf() throws Exception {
        String body = mapper.writeValueAsString(validBody());
        mvc.perform(get("/api/admin/movies")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "MANAGER")) {
            mvc.perform(post("/api/admin/movies").with(user("account").roles(role)).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/movies/10").with(user("account").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        verify(movies, never()).save(any());
    }

    @Test
    void createsMovieWithMultipleExistingGenresAndAllMetadata() throws Exception {
        mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.genres.length()").value(2))
                .andExpect(jsonPath("$.director").value("Đạo diễn B"));
        verify(movies).save(argThat(m -> m.getTitle().equals("Phim mới") && m.getDurationMinutes() == 120
                && m.getAgeRating().equals("T16") && m.getStatus() == MovieStatus.COMING_SOON
                && m.getReleaseDate().equals(LocalDate.of(2027, 2, 1))
                && m.getGenres().containsAll(Set.of(action, comedy))));
    }

    @Test
    void editingReplacesGenresWithoutChangingMovieIdentity() throws Exception {
        Map<String, Object> body = validBody();
        body.put("title", "Tên phim đã sửa"); body.put("genreIds", List.of(2));
        body.put("posterUrl", null); body.put("trailerUrl", null);
        mvc.perform(put("/api/admin/movies/10").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Tên phim đã sửa"))
                .andExpect(jsonPath("$.genres.length()").value(1))
                .andExpect(jsonPath("$.genres[0].id").value(2));
        assertEquals(Set.of(comedy), movie.getGenres());
        assertNull(movie.getPosterUrl()); assertNull(movie.getTrailerUrl());
    }

    @Test
    void unknownGenrePreventsMovieSave() throws Exception {
        Map<String, Object> body = validBody(); body.put("genreIds", List.of(1, 999));
        mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        verify(movies, never()).save(any());
    }

    @Test
    void rejectsInvalidMovieFieldsAndUnsafeMediaUrls() throws Exception {
        for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
                Map.entry("title", " "), Map.entry("durationMinutes", 0), Map.entry("durationMinutes", 601),
                Map.entry("ageRating", "R99"), Map.entry("genreIds", List.of()),
                Map.entry("posterUrl", "javascript:alert(1)"), Map.entry("trailerUrl", "file:///C:/private.txt"))) {
            Map<String, Object> body = validBody(); body.put(invalid.getKey(), invalid.getValue());
            mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").exists());
        verify(movies, never()).save(any());
    }

    @Test
    void supportsAllFourMovieStatusesAndRejectsUnknownStatus() throws Exception {
        for (MovieStatus value : MovieStatus.values()) {
            mvc.perform(patch("/api/admin/movies/10/status").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content("{\"status\":\"" + value + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value.name()));
            assertEquals(value, movie.getStatus());
        }
        mvc.perform(patch("/api/admin/movies/10/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/movies/10/status").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsRenamesAndDeletesUnusedGenre() throws Exception {
        mvc.perform(post("/api/admin/genres").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"genreName\":\" Khoa học viễn tưởng \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.genreName").value("Khoa học viễn tưởng"));
        mvc.perform(put("/api/admin/genres/1").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"genreName\":\"Phiêu lưu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.genreName").value("Phiêu lưu"));
        mvc.perform(delete("/api/admin/genres/1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(genres).delete(action);
    }

    @Test
    void duplicateGenresAndDeletingAssignedGenreReturnConflict() throws Exception {
        when(genres.existsByGenreNameIgnoreCase("Hành động")).thenReturn(true);
        mvc.perform(post("/api/admin/genres").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"genreName\":\"Hành động\"}"))
                .andExpect(status().isConflict());
        when(genres.existsByGenreNameIgnoreCaseAndIdNot("Hài", 1)).thenReturn(true);
        mvc.perform(put("/api/admin/genres/1").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"genreName\":\"Hài\"}"))
                .andExpect(status().isConflict());
        when(movies.existsByGenresId(1)).thenReturn(true);
        mvc.perform(delete("/api/admin/genres/1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        verify(genres, never()).delete(any());
    }

    @Test
    void invalidGenreAndMissingMovieReturnUsefulErrors() throws Exception {
        mvc.perform(post("/api/admin/genres").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"genreName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/movies/999")).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/movies/999").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/genres/999").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/movies").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile moviePart(Map<String, Object> body) throws Exception {
        return new MockMultipartFile("movie", "movie.json", "application/json", mapper.writeValueAsBytes(body));
    }

    private MockMultipartFile imagePart(String name) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return new MockMultipartFile("poster", name, "image/jpeg", bytes.toByteArray());
    }

    @Test
    void multipartStoresRealImageAndSavesOnlyPublicPathWithNormalizedTrailer() throws Exception {
        Map<String, Object> body = validBody(); body.put("trailerUrl", "https://youtu.be/M7lc1UVf-VE?si=tracking");
        String json = mvc.perform(multipart("/api/admin/movies").file(moviePart(body)).file(imagePart("../../unsafe.png"))
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.trailerUrl").value("https://www.youtube.com/watch?v=M7lc1UVf-VE"))
                .andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(json).path("posterUrl").asText(); createdPosters.add(url);
        assertTrue(url.matches("/uploads/posters/[a-f0-9]{32}\\.jpg"));
        verify(movies).save(argThat(m -> url.equals(m.getPosterUrl())));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(head(url)).andExpect(status().isOk());
        assertNotNull(ImageIO.read(posters.load(url.substring(MoviePosterStorage.PUBLIC_PREFIX.length())).getInputStream()));
    }

    @Test
    void multipartUpdatesSameMovieAndJsonCanKeepUploadedPoster() throws Exception {
        String json = mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/admin/movies/10")
                .file(moviePart(validBody())).file(imagePart("new.jpg")).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10)).andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(json).path("posterUrl").asText(); createdPosters.add(url);
        Map<String, Object> body = validBody(); body.put("posterUrl", url);
        mvc.perform(put("/api/admin/movies/10").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posterUrl").value(url));
    }

    @Test
    void uploadingNeedsAdminAndFreshCsrf() throws Exception {
        mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody())).file(imagePart("a.jpg")).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody())).file(imagePart("a.jpg"))
                .with(user("customer").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody())).file(imagePart("a.jpg"))
                .with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verify(movies, never()).save(any());
    }

    @Test
    void rejectsFakeEmptyActiveAndOversizedImagesWithoutSaving() throws Exception {
        for (MockMultipartFile file : List.of(
                new MockMultipartFile("poster", "fake.png", "image/png", "<script>alert(1)</script>".getBytes()),
                new MockMultipartFile("poster", "vector.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("poster", "empty.jpg", "image/jpeg", new byte[0]))) {
            mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody())).file(file)
                    .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody()))
                .file(new MockMultipartFile("poster", "big.jpg", "image/jpeg", new byte[(int) MoviePosterStorage.MAX_BYTES + 1]))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isPayloadTooLarge());
        verify(movies, never()).save(any());
        try (var files = Files.list(Config.posterDirectory)) { assertEquals(0, files.count()); }
    }

    @Test
    void failedDatabaseWriteRemovesNewFileAndBadMetadataDoesNotUpload() throws Exception {
        Map<String, Object> invalid = validBody(); invalid.put("title", " ");
        mvc.perform(multipart("/api/admin/movies").file(moviePart(invalid)).file(imagePart("valid.jpg"))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        when(movies.save(any(Movie.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("test"));
        mvc.perform(multipart("/api/admin/movies").file(moviePart(validBody())).file(imagePart("valid.jpg"))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        try (var files = Files.list(Config.posterDirectory)) { assertEquals(0, files.count()); }
    }

    @Test
    void rejectsMalformedYoutubeAndCredentialLinksAndDoesNotExposeArbitraryFiles() throws Exception {
        for (String url : List.of("https://www.youtube.com/watch?v=bad", "https://youtube.com/playlist?list=abc", "https://user:pass@example.com/video.mp4")) {
            Map<String, Object> body = validBody(); body.put("trailerUrl", url);
            mvc.perform(post("/api/admin/movies").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(mapper.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/uploads/posters/application.properties")).andExpect(status().isNotFound());
        mvc.perform(get("/uploads/posters/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg")).andExpect(status().isNotFound());
        verify(movies, never()).save(any());
    }
}
