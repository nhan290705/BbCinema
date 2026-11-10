package com.example.bbcinema;

import com.example.bbcinema.config.PasswordConfig;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.movie.Movie;
import com.example.bbcinema.model.movie.Showtime;
import com.example.bbcinema.repository.*;
import com.example.bbcinema.service.DemoDataService;
import com.example.bbcinema.service.ShowtimeManagementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: writes are isolated in a transaction and verified absent after rollback. */
@EnabledIfEnvironmentVariable(named = "BBCINEMA_DATABASE_TEST", matches = "true")
@SpringBootTest(classes = DemoDataDatabaseTest.DatabaseConfig.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "bbcinema.open-browser=false"
})
@Transactional
class DemoDataDatabaseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.example.bbcinema.model")
    @EnableJpaRepositories("com.example.bbcinema.repository")
    @Import({DemoDataService.class, ShowtimeManagementService.class, PasswordConfig.class})
    static class DatabaseConfig {
        @Bean Clock cinemaClock() {
            return Clock.fixed(Instant.parse("2099-06-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired DemoDataService demo;
    @Autowired ShowtimeManagementService scheduling;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired MovieRepository movies;
    @Autowired PasswordEncoder encoder;

    private static final List<String> TABLES = List.of("provinces", "cinemas", "auditoriums", "seats", "genres",
            "movies", "showtimes", "showtime_prices", "roles", "seat_types", "users");
    private final Map<String, Set<Long>> before = new LinkedHashMap<>(), added = new LinkedHashMap<>();

    @BeforeEach
    void baseline() { TABLES.forEach(table -> before.put(table, ids(table))); }

    @Test
    void seedIsIdempotentAndProducesACompletePricedCustomerJourney() {
        var existingAdmin = users.findByEmailIgnoreCase("admin@gmail.com");
        String originalHash = existingAdmin.map(u -> u.getPasswordHash()).orElse(null);
        Integer originalRole = existingAdmin.map(u -> u.getRole().getId()).orElse(null);
        UserStatus originalStatus = existingAdmin.map(u -> u.getStatus()).orElse(null);
        demo.seed();
        em.flush();
        Map<String, Long> counts = new LinkedHashMap<>();
        TABLES.forEach(table -> counts.put(table, count(table)));
        var repeated = demo.seed();
        em.flush();
        assertEquals(0, repeated.totalCreated(), "A second seed must not add duplicate catalog rows, seats or time slots");
        TABLES.forEach(table -> assertEquals(counts.get(table), count(table), table));

        assertEquals(3L, scalar("select count(*) from provinces where code in ('DEMO_HN','DEMO_DN','DEMO_HCM')"));
        assertTrue(scalar("select count(*) from cinemas where cinema_name like '%(Mẫu)'") >= 3);
        assertTrue(scalar("select count(*) from movies where poster_url like '/demo/posters/%'") >= 6);

        var page = scheduling.listShowtimes(null, null, LocalDate.of(2099, 6, 2), null, 0, 100, false);
        assertFalse(page.items().isEmpty(), "The sample has upcoming sales visible through the real customer query");
        var map = scheduling.getSeatMap(page.items().get(0).id());
        assertFalse(map.seats().isEmpty());
        assertTrue(map.seats().stream().anyMatch(s -> s.seatSpan() == 2 && s.price() != null && s.price().intValueExact() == 150000));
        assertTrue(map.seats().stream().filter(s -> s.status() == SeatStatus.AISLE).allMatch(s -> s.price() == null && !s.usable()));
        assertTrue(map.seats().stream().filter(s -> s.status() == SeatStatus.BROKEN || s.status() == SeatStatus.BLOCKED).noneMatch(s -> s.usable()));
        if (existingAdmin.isPresent()) {
            var admin = users.findByEmailIgnoreCase("admin@gmail.com").orElseThrow();
            assertEquals(originalHash, admin.getPasswordHash());
            assertEquals(originalRole, admin.getRole().getId());
            assertEquals(originalStatus, admin.getStatus());
        } else {
            users.findByEmailIgnoreCase("admin@gmail.com").ifPresent(admin -> {
                assertTrue(encoder.matches("12345678", admin.getPasswordHash()));
                assertEquals("admin", admin.getRole().getRoleName().toLowerCase(Locale.ROOT));
            });
        }
        rememberAdded();
    }

    @Test
    void rerunningSeedPreservesAnEditedMovieAndCancelledSampleShowtime() {
        demo.seed();
        em.flush();
        Movie movie = em.createQuery("select m from Movie m where m.posterUrl = '/demo/posters/summer-train.svg'", Movie.class)
                .getSingleResult();
        movie.setTitle("Tên phim đã được quản trị viên chỉnh sửa");
        movie.setDescription("Nội dung do quản trị viên nhập, cần được giữ nguyên.");
        movie.setStatus(MovieStatus.HIDDEN);
        movies.save(movie);
        Showtime sale = em.createQuery("select s from Showtime s where s.startTime >= :from and s.startTime < :to and s.status = :status order by s.id", Showtime.class)
                .setParameter("from", LocalDate.of(2099, 6, 1).atStartOfDay())
                .setParameter("to", LocalDate.of(2099, 6, 4).atStartOfDay())
                .setParameter("status", ShowtimeStatus.OPEN_FOR_SALE).setMaxResults(1).getSingleResult();
        scheduling.updateStatus(sale.getId(), new com.example.bbcinema.dto.ShowtimeDtos.StatusRequest(ShowtimeStatus.CANCELLED));
        var editedRoom = sale.getAuditorium();
        editedRoom.setStatus(AuditoriumStatus.MAINTENANCE);
        em.flush();
        long movieCount = count("movies"), showCount = count("showtimes");
        var repeat = demo.seed();
        em.flush();
        assertEquals(0, repeat.totalCreated());
        assertEquals(movieCount, count("movies"));
        assertEquals(showCount, count("showtimes"));
        assertEquals("Tên phim đã được quản trị viên chỉnh sửa", movie.getTitle());
        assertEquals("Nội dung do quản trị viên nhập, cần được giữ nguyên.", movie.getDescription());
        assertEquals(MovieStatus.HIDDEN, movie.getStatus());
        assertEquals(AuditoriumStatus.MAINTENANCE, editedRoom.getStatus());
        assertEquals(ShowtimeStatus.CANCELLED, sale.getStatus());
        rememberAdded();
    }

    private Set<Long> ids(String table) {
        Set<Long> result = new HashSet<>();
        for (Object value : em.createNativeQuery("select id from " + table).getResultList()) result.add(((Number) value).longValue());
        return result;
    }
    private long count(String table) { return scalar("select count(*) from " + table); }
    private long scalar(String sql) { return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue(); }
    private void rememberAdded() {
        em.flush();
        TABLES.forEach(table -> { Set<Long> fresh = ids(table); fresh.removeAll(before.get(table)); added.put(table, fresh); });
    }
    @AfterTransaction
    void rollbackLeavesNoCreatedRecords() {
        added.forEach((table, fresh) -> {
            if (!fresh.isEmpty()) {
                Number remaining = (Number) em.createNativeQuery("select count(*) from " + table + " where id in (:ids)")
                        .setParameter("ids", fresh).getSingleResult();
                assertEquals(0L, remaining.longValue(), "All demo test inserts must roll back: " + table);
            }
        });
    }
}
