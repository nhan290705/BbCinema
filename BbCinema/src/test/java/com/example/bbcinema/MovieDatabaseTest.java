package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.controller.ApiExceptionHandler;
import com.example.bbcinema.controller.MovieAdminController;
import com.example.bbcinema.controller.MovieCatalogController;
import com.example.bbcinema.controller.MoviePosterController;
import com.example.bbcinema.repository.GenreRepository;
import com.example.bbcinema.repository.MovieRepository;
import com.example.bbcinema.security.CustomUserDetailsService;
import com.example.bbcinema.security.SecurityConfig;
import com.example.bbcinema.service.MovieManagementService;
import com.example.bbcinema.service.MoviePosterStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.junit.jupiter.api.AfterAll;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in PostgreSQL verification: existing schema only, no seed runners, all test rows roll back. */
@EnabledIfEnvironmentVariable(named = "BBCINEMA_DATABASE_TEST", matches = "true")
@SpringBootTest(classes = MovieDatabaseTest.DatabaseConfig.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "movie-database-test", roles = "ADMIN")
class MovieDatabaseTest {
    private static final Path POSTER_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "bbcinema-movie-db-" + UUID.randomUUID());
    @DynamicPropertySource
    static void posterDirectory(DynamicPropertyRegistry registry) { registry.add("bbcinema.media.poster-directory", POSTER_DIRECTORY::toString); }
    @AfterAll
    static void removeEmptyPosterDirectory() throws Exception { Files.deleteIfExists(POSTER_DIRECTORY); }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.example.bbcinema.model")
    @EnableJpaRepositories("com.example.bbcinema.repository")
    @Import({MovieCatalogController.class, MovieAdminController.class, MoviePosterController.class, MovieManagementService.class, MoviePosterStorage.class,
            ApiExceptionHandler.class, SecurityConfig.class, PasswordConfig.class, CustomUserDetailsService.class})
    static class DatabaseConfig {}

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired MovieRepository movies;
    @Autowired GenreRepository genres;
    @Autowired MoviePosterStorage posters;
    private final List<Long> movieIds = new ArrayList<>();
    private final List<Integer> genreIds = new ArrayList<>();
    private final List<String> posterUrls = new ArrayList<>();

    @Test
    void posterUploadPersistsRelativePathAndReplacementKeepsMovieIdentity() throws Exception {
        int genre = createGenre(unique() + " Genre");
        Map<String, Object> body = new HashMap<>(movieBody(unique() + " Poster", "NOW_SHOWING", List.of(genre)));
        body.put("trailerUrl", "https://www.youtube.com/shorts/M7lc1UVf-VE");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB), "png", png);
        var image = new MockMultipartFile("poster", "poster.png", "image/png", png.toByteArray());
        var metadata = new MockMultipartFile("movie", "movie.json", "application/json", mapper.writeValueAsBytes(body));
        String response = mvc.perform(multipart("/api/admin/movies").file(metadata).file(image).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = mapper.readTree(response); long id = created.path("id").asLong(); movieIds.add(id);
        String first = created.path("posterUrl").asText(); posterUrls.add(first);
        assertTrue(first.matches("/uploads/posters/[a-f0-9]{32}\\.png"));
        flushAndClear();
        assertEquals(first, entityManager.createNativeQuery("select poster_url from movies where id = :id").setParameter("id", id).getSingleResult());
        assertEquals("https://www.youtube.com/watch?v=M7lc1UVf-VE", movies.findById(id).orElseThrow().getTrailerUrl());
        mvc.perform(get(first).with(anonymous())).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        String updatedJson = mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/admin/movies/" + id)
                .file(metadata).file(image).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mapper.readTree(updatedJson).path("posterUrl").asText(); posterUrls.add(second);
        assertNotEquals(first, second); flushAndClear();
        assertEquals(second, movies.findById(id).orElseThrow().getPosterUrl());
        assertNotNull(ImageIO.read(posters.load(second.substring(MoviePosterStorage.PUBLIC_PREFIX.length())).getInputStream()));
        assertEquals(1, linkCount(id));
    }

    @Test
    void publicQueriesExcludeHiddenFromContentAndCountsWhileGenresAndPaginationWork() throws Exception {
        String prefix = unique();
        int action = createGenre(prefix + " Action"), comedy = createGenre(prefix + " Comedy");
        long now = createMovie(prefix + " Present", "NOW_SHOWING", List.of(action, comedy));
        createMovie(prefix + " Future", "COMING_SOON", List.of(action));
        long ended = createMovie(prefix + " Past", "ENDED", List.of(action));
        long hidden = createMovie(prefix + " Secret", "HIDDEN", List.of(action, comedy));
        flushAndClear();

        JsonNode firstPage = readJson("/api/movies?title=" + prefix + "&page=0&size=2");
        assertEquals(3, firstPage.path("totalElements").asInt());
        assertEquals(2, firstPage.path("totalPages").asInt());
        assertEquals(2, firstPage.path("items").size());
        for (JsonNode film : firstPage.path("items")) assertNotEquals(hidden, film.path("id").asLong());
        JsonNode secondPage = readJson("/api/movies?title=" + prefix + "&page=1&size=2");
        assertEquals(1, secondPage.path("items").size());
        assertEquals(3, secondPage.path("totalElements").asInt());

        JsonNode filtered = readJson("/api/movies?title=" + prefix + "&genreId=" + comedy);
        assertEquals(1, filtered.path("totalElements").asInt());
        assertEquals(now, filtered.path("items").get(0).path("id").asLong());
        assertEquals(2, filtered.path("items").get(0).path("genres").size());
        JsonNode showing = readJson("/api/movies?title=" + prefix + "&status=NOW_SHOWING");
        assertEquals(1, showing.path("totalElements").asInt());
        assertEquals(now, showing.path("items").get(0).path("id").asLong());

        mvc.perform(get("/api/movies/" + hidden).with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get("/api/movies/" + ended).with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(get("/api/admin/movies/" + hidden)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"));
        mvc.perform(get("/api/admin/movies").param("title", prefix)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/admin/movies").param("title", prefix).param("status", "HIDDEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void titleSearchTreatsSqlWildcardCharactersAsLiteralText() throws Exception {
        String prefix = unique();
        int genre = createGenre(prefix + " Genre");
        long percent = createMovie(prefix + " 100% Match", "NOW_SHOWING", List.of(genre));
        createMovie(prefix + " 100X Match", "NOW_SHOWING", List.of(genre));
        long underscore = createMovie(prefix + " A_B", "COMING_SOON", List.of(genre));
        createMovie(prefix + " AXB", "COMING_SOON", List.of(genre));
        flushAndClear();

        mvc.perform(get("/api/movies").with(anonymous()).param("title", prefix + " 100%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value((int) percent));
        mvc.perform(get("/api/movies").with(anonymous()).param("title", prefix + " A_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value((int) underscore));
    }

    @Test
    void editingReplacesJoinTableLinksAndOnlyUnassignedGenresCanBeDeleted() throws Exception {
        String prefix = unique();
        int first = createGenre(prefix + " First"), second = createGenre(prefix + " Second"), third = createGenre(prefix + " Third");
        long movieId = createMovie(prefix + " Movie", "COMING_SOON", List.of(first, second));
        flushAndClear();
        assertEquals(2, linkCount(movieId));

        mvc.perform(put("/api/admin/movies/" + movieId).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(movieBody(prefix + " Edited", "NOW_SHOWING", List.of(second, third)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.genres.length()").value(2));
        flushAndClear();
        Set<Integer> linked = movies.findById(movieId).orElseThrow().getGenres().stream()
                .map(g -> g.getId()).collect(Collectors.toSet());
        assertEquals(Set.of(second, third), linked);
        assertEquals(2, linkCount(movieId));
        Number removedLink = (Number) entityManager.createNativeQuery(
                "select count(*) from movie_genres where movie_id = :movieId and genre_id = :genreId")
                .setParameter("movieId", movieId).setParameter("genreId", first).getSingleResult();
        assertEquals(0, removedLink.intValue());

        mvc.perform(delete("/api/admin/genres/" + second).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/genres/" + first).with(csrf())).andExpect(status().isNoContent());
        flushAndClear();
        assertFalse(genres.existsById(first));
        assertTrue(genres.existsById(second));

        mvc.perform(put("/api/admin/genres/" + second).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("genreName", prefix + " Renamed"))))
                .andExpect(status().isOk());
        flushAndClear();
        JsonNode detail = readJson("/api/movies/" + movieId);
        boolean renamed = false;
        for (JsonNode item : detail.path("genres")) {
            if (item.path("id").asInt() == second) renamed = item.path("genreName").asText().equals(prefix + " Renamed");
        }
        assertTrue(renamed, "Genre rename must be reflected on the linked public movie detail");
    }

    private int createGenre(String name) throws Exception {
        JsonNode result = postJson("/api/admin/genres", Map.of("genreName", name));
        int id = result.path("id").asInt(); genreIds.add(id); return id;
    }

    private long createMovie(String title, String status, List<Integer> selectedGenres) throws Exception {
        JsonNode result = postJson("/api/admin/movies", movieBody(title, status, selectedGenres));
        long id = result.path("id").asLong(); movieIds.add(id); return id;
    }

    private Map<String, Object> movieBody(String title, String status, List<Integer> selectedGenres) {
        return Map.of("title", title, "description", "Nội dung kiểm thử rollback", "durationMinutes", 100,
                "ageRating", "T13", "releaseDate", "2027-01-01", "status", status, "genreIds", selectedGenres,
                "posterUrl", "https://example.com/poster.jpg", "trailerUrl", "https://example.com/trailer");
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        String json = mvc.perform(post(path).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode readJson(String path) throws Exception {
        String json = mvc.perform(get(path).with(anonymous())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private int linkCount(long movieId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from movie_genres where movie_id = :id")
                .setParameter("id", movieId).getSingleResult()).intValue();
    }

    private void flushAndClear() { entityManager.flush(); entityManager.clear(); }
    private String unique() { return "MOVIETEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }

    @AfterTransaction
    void testOwnedRecordsAndJoinRowsWereRolledBack() {
        for (Long id : movieIds) {
            assertFalse(movies.existsById(id), "Test movie must roll back");
            assertEquals(0, linkCount(id), "Test join-table rows must roll back");
        }
        for (Integer id : genreIds) assertFalse(genres.existsById(id), "Test genre must roll back");
        for (String url : posterUrls) assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> posters.load(url.substring(MoviePosterStorage.PUBLIC_PREFIX.length())), "Uncommitted poster must be removed after rollback");
    }
}
