package com.example.bbcinema.service;

import com.example.bbcinema.dto.ShowtimeDtos.*;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.model.movie.*;
import com.example.bbcinema.model.user.Role;
import com.example.bbcinema.model.user.User;
import com.example.bbcinema.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Additive local demo data. Existing account credentials, catalog edits and bookings are never reset. */
@Service
public class DemoDataService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PHONE = "0900000000";
    private final ProvinceRepository provinces;
    private final CinemaRepository cinemas;
    private final AuditoriumRepository auditoriums;
    private final SeatTypeRepository seatTypes;
    private final SeatRepository seats;
    private final GenreRepository genres;
    private final MovieRepository movies;
    private final RoleRepository roles;
    private final UserRepository users;
    private final ShowtimeRepository showtimes;
    private final ShowtimeManagementService scheduling;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final EntityManager em;

    public DemoDataService(ProvinceRepository provinces, CinemaRepository cinemas, AuditoriumRepository auditoriums,
                           SeatTypeRepository seatTypes, SeatRepository seats, GenreRepository genres,
                           MovieRepository movies, RoleRepository roles, UserRepository users,
                           ShowtimeRepository showtimes, ShowtimeManagementService scheduling,
                           PasswordEncoder encoder, Clock clock, EntityManager em) {
        this.provinces = provinces; this.cinemas = cinemas; this.auditoriums = auditoriums;
        this.seatTypes = seatTypes; this.seats = seats; this.genres = genres; this.movies = movies;
        this.roles = roles; this.users = users; this.showtimes = showtimes; this.scheduling = scheduling;
        this.encoder = encoder; this.clock = clock; this.em = em;
    }

    public record SeedReport(int provinces, int cinemas, int auditoriums, int seats, int genres,
                             int movies, int showtimes, int adminAccounts) {
        public int totalCreated() {
            return provinces + cinemas + auditoriums + seats + genres + movies + showtimes + adminAccounts;
        }
    }

    @Transactional
    public SeedReport seed() {
        // Serialize startup seeds across two local Bbcinema processes without touching other application records.
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(2026102904)").getSingleResult();
        Counts added = new Counts();
        createAdminIfMissing(added);
        SeatType standard = seatType("STANDARD", 1, BigDecimal.ZERO);
        SeatType vip = seatType("VIP", 1, new BigDecimal("20000"));
        SeatType couple = seatType("COUPLE", 2, new BigDecimal("75000"));
        List<Genre> catalog = List.of(genre("Hành động", added), genre("Phiêu lưu", added),
                genre("Tâm lý", added), genre("Gia đình", added), genre("Khoa học viễn tưởng", added));
        LocalDate today = LocalDate.now(clock);
        List<Movie> demoMovies = createMovies(catalog, today, added);
        List<Auditorium> demoRooms = createCinemas(added);
        demoRooms.sort(Comparator.comparing(Auditorium::getId));
        // The same ordered room locks are used by the scheduling service when a showtime changes rooms.
        for (Auditorium room : demoRooms) em.refresh(room, LockModeType.PESSIMISTIC_WRITE);
        for (Auditorium room : demoRooms) createSeatsIfEmpty(room, standard, vip, couple, added);
        createShowtimes(demoRooms, demoMovies, today, added);
        SeedReport report = added.report();
        log.info("Bbcinema demo data: added {} provinces, {} cinemas, {} rooms, {} seats, {} genres, {} movies, {} showtimes, {} admin accounts",
                report.provinces(), report.cinemas(), report.auditoriums(), report.seats(), report.genres(),
                report.movies(), report.showtimes(), report.adminAccounts());
        return report;
    }

    private void createAdminIfMissing(Counts added) {
        if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) return;
        if (users.existsByPhone(ADMIN_PHONE)) {
            log.warn("Demo admin was not created: its sample phone number is already used. Existing accounts were preserved.");
            return;
        }
        Role admin = roles.findByRoleNameIgnoreCase("Admin").orElseGet(() -> {
            Role role = new Role(); role.setRoleName("Admin"); return roles.save(role);
        });
        User account = new User();
        account.setFullName("Admin Bbcinema"); account.setEmail(ADMIN_EMAIL); account.setPhone(ADMIN_PHONE);
        account.setPasswordHash(encoder.encode("12345678")); account.setRole(admin);
        account.setStatus(UserStatus.ACTIVE); account.setCurrentPoints(0);
        users.save(account); added.adminAccounts++;
    }

    private SeatType seatType(String name, int span, BigDecimal surcharge) {
        return seatTypes.findByTypeNameIgnoreCase(name).orElseGet(() -> {
            SeatType type = new SeatType(); type.setTypeName(name); type.setSeatSpan(span);
            type.setDefaultSurcharge(surcharge); return seatTypes.save(type);
        });
    }

    private Genre genre(String name, Counts added) {
        List<Genre> found = em.createQuery("select g from Genre g where lower(g.genreName) = lower(:name)", Genre.class)
                .setParameter("name", name).setMaxResults(1).getResultList();
        if (!found.isEmpty()) return found.get(0);
        Genre genre = new Genre(); genre.setGenreName(name); added.genres++; return genres.save(genre);
    }

    private List<Movie> createMovies(List<Genre> catalog, LocalDate today, Counts added) {
        List<Movie> result = new ArrayList<>();
        result.add(movie("Chuyến Tàu Qua Mùa Hạ", "summer-train", 110, "P", MovieStatus.NOW_SHOWING,
                "Trên chuyến tàu ven biển, một nhiếp ảnh gia trẻ tìm lại cuốn nhật ký của mẹ. Những ga nhỏ và người bạn đồng hành bất ngờ giúp cô ghép lại một mùa hạ chưa từng biết.",
                "Minh An, Hải Nam, Bảo Ngọc", "Lâm Minh", today.minusDays(8), Set.of(catalog.get(1), catalog.get(2)), added));
        result.add(movie("Thành Phố Sau Cơn Mưa", "rain-city", 105, "T13", MovieStatus.NOW_SHOWING,
                "Một quán cà phê mở cửa xuyên đêm trở thành nơi giao nhau của ba cuộc đời. Giữa thành phố vừa qua mùa mưa, mỗi người học cách bắt đầu lại từ điều giản dị nhất.",
                "Thanh Hạ, Hoàng Sơn, Diệp Vy", "Ngọc Châu", today.minusDays(4), Set.of(catalog.get(2)), added));
        result.add(movie("Mật Mã Thiên Hà", "galaxy-code", 125, "T16", MovieStatus.NOW_SHOWING,
                "Một tín hiệu cổ xuất hiện trên trạm quan sát ở rìa thiên hà. Đội thám hiểm phải giải mã nó trước khi cơn bão từ cắt đứt con đường trở về Trái Đất.",
                "Khánh Vũ, An Nhiên, Tuấn Phong", "Trần Vũ", today.minusDays(2), Set.of(catalog.get(0), catalog.get(4)), added));
        result.add(movie("Nhà Có Một Vì Sao", "little-star", 100, "P", MovieStatus.COMING_SOON,
                "Cậu bé tin rằng người hàng xóm mới là nhà du hành vũ trụ. Cuộc điều tra ngây ngô kéo cả gia đình vào hành trình tìm lại những giấc mơ họ đã bỏ quên.",
                "Gia Bảo, Mai Anh, Nhật Quang", "Hà Vy", today.plusDays(1), Set.of(catalog.get(3), catalog.get(1)), added));
        result.add(movie("Bên Kia Đại Dương", "ocean-side", 115, "T13", MovieStatus.ENDED,
                "Hai chị em trở về ngôi làng chài sau nhiều năm xa cách. Một lá thư cũ dẫn họ đến bí mật của cha và hành trình hòa giải với chính mình.",
                "Thảo Linh, Bích Hà, Hữu Tín", "Đỗ Hải", today.minusDays(45), Set.of(catalog.get(2), catalog.get(3)), added));
        result.add(movie("Những Ngày Màu Ngọc", "jade-days", 108, "K", MovieStatus.HIDDEN,
                "Một nhóm bạn biến rạp chiếu bỏ quên thành câu lạc bộ phim. Mỗi buổi chiếu mang đến một câu chuyện, một người bạn và một quyết định can đảm mới.",
                "Hoài An, Phúc Khang, Thiên Lam", "Lê Ngọc", today.plusDays(10), Set.of(catalog.get(3), catalog.get(2)), added));
        return result;
    }

    private Movie movie(String title, String slug, int duration, String age, MovieStatus status, String synopsis,
                         String cast, String director, LocalDate release, Set<Genre> selected, Counts added) {
        String poster = "/demo/posters/" + slug + ".svg";
        List<Movie> found = em.createQuery("select m from Movie m where m.posterUrl = :poster or m.title = :title order by m.id", Movie.class)
                .setParameter("poster", poster).setParameter("title", title).setMaxResults(1).getResultList();
        if (!found.isEmpty()) return found.get(0);
        Movie movie = new Movie(); movie.setTitle(title); movie.setDurationMinutes(duration); movie.setAgeRating(age);
        movie.setDescription("Phim hư cấu trong bộ dữ liệu mẫu Bbcinema. " + synopsis);
        movie.setCastList(cast + " (nhân vật mẫu)"); movie.setDirector(director + " (mẫu)");
        movie.setPosterUrl(poster); movie.setStatus(status); movie.setReleaseDate(release);
        movie.getGenres().addAll(selected); added.movies++; return movies.save(movie);
    }

    private List<Auditorium> createCinemas(Counts added) {
        String[][] locations = {{"DEMO_HN", "Hà Nội", "Bbcinema Central (Mẫu)"},
                {"DEMO_DN", "Đà Nẵng", "Bbcinema Ocean (Mẫu)"},
                {"DEMO_HCM", "TP. Hồ Chí Minh", "Bbcinema Riverside (Mẫu)"}};
        List<Auditorium> result = new ArrayList<>();
        for (String[] location : locations) {
            List<Province> existing = em.createQuery("select p from Province p where p.code = :code", Province.class)
                    .setParameter("code", location[0]).setMaxResults(1).getResultList();
            Province province;
            if (existing.isEmpty()) {
                province = new Province(); province.setProvinceName(location[1]); province.setCode(location[0]);
                province = provinces.save(province); added.provinces++;
            } else province = existing.get(0);
            List<Cinema> existingCinemas = em.createQuery("select c from Cinema c where c.cinemaName = :name order by c.id", Cinema.class)
                    .setParameter("name", location[2]).setMaxResults(1).getResultList();
            Cinema cinema;
            if (existingCinemas.isEmpty()) {
                cinema = new Cinema(); cinema.setCinemaName(location[2]); cinema.setProvince(province);
                cinema.setAddress("Địa chỉ minh họa trong bộ dữ liệu mẫu tại " + location[1]);
                cinema.setHotline("1900 0000"); cinema.setStatus(CinemaStatus.ACTIVE);
                cinema = cinemas.save(cinema); added.cinemas++;
            } else cinema = existingCinemas.get(0);
            for (int index = 1; index <= 2; index++) {
                String name = "Phòng mẫu 0" + index;
                List<Auditorium> existingRooms = em.createQuery("select a from Auditorium a where a.cinema.id = :cinemaId and a.name = :name order by a.id", Auditorium.class)
                        .setParameter("cinemaId", cinema.getId()).setParameter("name", name).setMaxResults(1).getResultList();
                if (!existingRooms.isEmpty()) result.add(existingRooms.get(0));
                else {
                    Auditorium room = new Auditorium(); room.setCinema(cinema); room.setName(name);
                    room.setTotalRows(6); room.setTotalCols(10); room.setStatus(AuditoriumStatus.ACTIVE);
                    result.add(auditoriums.save(room)); added.auditoriums++;
                }
            }
        }
        return result;
    }

    private void createSeatsIfEmpty(Auditorium room, SeatType standard, SeatType vip, SeatType couple, Counts added) {
        if (seats.existsByAuditoriumId(room.getId())) return;
        if (room.getTotalRows() != 6 || room.getTotalCols() != 10
                || standard.getSeatSpan() != 1 || vip.getSeatSpan() != 1 || couple.getSeatSpan() != 2) {
            log.warn("Preserving edited demo room/type configuration for auditorium {}", room.getId()); return;
        }
        List<Seat> layout = new ArrayList<>();
        for (int row = 0; row < 6; row++) {
            SeatType type = row == 5 ? couple : row >= 3 ? vip : standard;
            int span = type.getSeatSpan();
            for (int column = 0; column < 10; column += span) {
                Seat seat = new Seat(); seat.setAuditorium(room); seat.setSeatType(type);
                seat.setRowLabel(String.valueOf((char) ('A' + row))); seat.setColNumber(column + 1);
                seat.setGridX(column); seat.setGridY(row);
                SeatStatus status = row == 0 && column == 0 ? SeatStatus.BROKEN
                        : row == 0 && column == 1 ? SeatStatus.BLOCKED
                        : row == 0 && column == 4 ? SeatStatus.AISLE : SeatStatus.AVAILABLE;
                seat.setStatus(status); layout.add(seat);
            }
        }
        seats.saveAll(layout); added.seats += layout.size();
    }

    private void createShowtimes(List<Auditorium> rooms, List<Movie> demoMovies, LocalDate today, Counts added) {
        List<Movie> eligible = demoMovies.stream().filter(m -> (m.getStatus() == MovieStatus.NOW_SHOWING || m.getStatus() == MovieStatus.COMING_SOON)
                && m.getDurationMinutes() != null && m.getDurationMinutes() >= 1 && m.getDurationMinutes() <= 150).toList();
        if (eligible.isEmpty()) return;
        int[][] hours = {{9, 15}, {12, 18}, {15, 21}, {9, 18}, {12, 21}, {15, 21}};
        LocalDateTime earliest = LocalDateTime.now(clock).plusMinutes(10);
        int ordinal = 0;
        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            Auditorium room = rooms.get(roomIndex);
            if (room.getStatus() != AuditoriumStatus.ACTIVE || room.getCinema().getStatus() != CinemaStatus.ACTIVE) continue;
            List<Seat> layout = seats.findByAuditoriumIdOrderByGridYAscGridXAsc(room.getId());
            if (layout.stream().noneMatch(s -> s.getStatus() == SeatStatus.AVAILABLE)) continue;
            List<PriceInput> priceList = prices(layout);
            for (int day = 0; day < 3; day++) {
                for (int slot = 0; slot < 2; slot++) {
                    int candidate = ordinal++;
                    LocalDateTime start = today.plusDays(day).atTime(hours[roomIndex % hours.length][slot], 0);
                    if (!start.isAfter(earliest)) continue;
                    boolean occupied = !em.createQuery("select s.id from Showtime s where s.auditorium.id = :room and s.startTime = :start", Long.class)
                            .setParameter("room", room.getId()).setParameter("start", start).setMaxResults(1).getResultList().isEmpty();
                    if (occupied) continue; // Includes cancellations: never recreate or reopen a show the user canceled.
                    Movie movie = eligible.get((roomIndex + day + slot) % eligible.size());
                    if (movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(start.toLocalDate())) {
                        movie = eligible.stream().filter(m -> m.getReleaseDate() == null || !m.getReleaseDate().isAfter(start.toLocalDate())).findFirst().orElse(null);
                        if (movie == null) continue;
                    }
                    LocalDateTime end = start.plusMinutes(movie.getDurationMinutes());
                    if (showtimes.hasOverlap(room.getId(), start, end, 0L)) continue;
                    ShowtimeResponse created = scheduling.createShowtime(new ShowtimeRequest(movie.getId(), room.getCinema().getId(),
                            room.getId(), start, new BigDecimal("75000"), priceList));
                    if (candidate % 11 == 0) scheduling.updateStatus(created.id(), new StatusRequest(ShowtimeStatus.CANCELLED));
                    else if (candidate % 4 != 0) scheduling.updateStatus(created.id(), new StatusRequest(ShowtimeStatus.OPEN_FOR_SALE));
                    added.showtimes++;
                }
            }
        }
    }

    private List<PriceInput> prices(List<Seat> layout) {
        Map<Integer, SeatType> required = new TreeMap<>();
        layout.stream().filter(s -> s.getStatus() != SeatStatus.AISLE).forEach(s -> required.put(s.getSeatType().getId(), s.getSeatType()));
        return required.values().stream().map(type -> {
            String name = type.getTypeName().trim().toUpperCase(Locale.ROOT);
            BigDecimal price = new BigDecimal(name.equals("COUPLE") || type.getSeatSpan() == 2 ? "150000" : name.equals("VIP") ? "95000" : "75000");
            return new PriceInput(type.getId(), price);
        }).toList();
    }

    private static class Counts {
        int provinces, cinemas, auditoriums, seats, genres, movies, showtimes, adminAccounts;
        SeedReport report() { return new SeedReport(provinces, cinemas, auditoriums, seats, genres, movies, showtimes, adminAccounts); }
    }
}
