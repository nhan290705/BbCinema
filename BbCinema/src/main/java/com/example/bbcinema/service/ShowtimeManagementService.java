package com.example.bbcinema.service;

import com.example.bbcinema.dto.ShowtimeDtos.*;
import com.example.bbcinema.enums.*;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.model.movie.*;
import com.example.bbcinema.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ShowtimeManagementService {
    private final MovieRepository movies;
    private final CinemaRepository cinemas;
    private final AuditoriumRepository auditoriums;
    private final SeatRepository seats;
    private final ShowtimeRepository showtimes;
    private final ShowtimePriceRepository prices;
    private final TicketRepository tickets;
    private final SeatHoldRepository holds;
    private final Clock clock;

    public ShowtimeManagementService(MovieRepository movies, CinemaRepository cinemas, AuditoriumRepository auditoriums,
                                     SeatRepository seats, ShowtimeRepository showtimes, ShowtimePriceRepository prices,
                                     TicketRepository tickets, SeatHoldRepository holds, Clock clock) {
        this.movies = movies;
        this.cinemas = cinemas;
        this.auditoriums = auditoriums;
        this.seats = seats;
        this.showtimes = showtimes;
        this.prices = prices;
        this.tickets = tickets;
        this.holds = holds;
        this.clock = clock;
    }

    public List<CinemaOption> listPublicCinemas() {
        return cinemas.findAll(Sort.by("cinemaName", "id")).stream()
                .filter(c -> c.getStatus() == CinemaStatus.ACTIVE)
                .map(c -> new CinemaOption(c.getId(), c.getCinemaName())).toList();
    }

    public ShowtimePage listShowtimes(Long movieId, Long cinemaId, LocalDate date, ShowtimeStatus status,
                                      int page, int size, boolean admin) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw bad("page từ 0–100000, size từ 1–100");
        if ((movieId != null && movieId < 1) || (cinemaId != null && cinemaId < 1)) throw bad("ID phim/rạp phải là số dương");
        if (!admin && status != null && status != ShowtimeStatus.OPEN_FOR_SALE) throw bad("Khách chỉ xem suất đang mở bán");
        LocalDateTime from = null, to = null;
        if (date != null) {
            try { from = date.atStartOfDay(); to = date.plusDays(1).atStartOfDay(); }
            catch (DateTimeException ex) { throw bad("Ngày chiếu không hợp lệ"); }
        }
        LocalDateTime dayStart = from, dayEnd = to, now = now();
        Specification<Showtime> spec = (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (movieId != null) filters.add(cb.equal(root.get("movie").get("id"), movieId));
            if (cinemaId != null) filters.add(cb.equal(root.get("auditorium").get("cinema").get("id"), cinemaId));
            if (dayStart != null) {
                filters.add(cb.greaterThanOrEqualTo(root.get("startTime"), dayStart));
                filters.add(cb.lessThan(root.get("startTime"), dayEnd));
            }
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (!admin) {
                filters.add(cb.equal(root.get("status"), ShowtimeStatus.OPEN_FOR_SALE));
                filters.add(cb.greaterThan(root.get("startTime"), now));
                filters.add(root.get("movie").get("status").in(MovieStatus.COMING_SOON, MovieStatus.NOW_SHOWING));
                filters.add(cb.equal(root.get("auditorium").get("status"), AuditoriumStatus.ACTIVE));
                filters.add(cb.equal(root.get("auditorium").get("cinema").get("status"), CinemaStatus.ACTIVE));
                // Public pagination/counts must also exclude layouts without usable seats or complete pricing.
                var available = query.subquery(Long.class);
                var availableSeat = available.from(Seat.class);
                available.select(availableSeat.get("id")).where(
                        cb.equal(availableSeat.get("auditorium"), root.get("auditorium")),
                        cb.equal(availableSeat.get("status"), SeatStatus.AVAILABLE));
                filters.add(cb.exists(available));
                var unpriced = query.subquery(Long.class);
                var unpricedSeat = unpriced.from(Seat.class);
                var price = unpriced.subquery(Long.class);
                var priceRow = price.from(ShowtimePrice.class);
                price.select(priceRow.get("id")).where(cb.equal(priceRow.get("showtime"), root),
                        cb.equal(priceRow.get("seatType"), unpricedSeat.get("seatType")), cb.ge(priceRow.get("price"), BigDecimal.ZERO));
                unpriced.select(unpricedSeat.get("id")).where(
                        cb.equal(unpricedSeat.get("auditorium"), root.get("auditorium")),
                        cb.notEqual(unpricedSeat.get("status"), SeatStatus.AISLE), cb.not(cb.exists(price)));
                filters.add(cb.not(cb.exists(unpriced)));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
        var result = showtimes.findAll(spec, PageRequest.of(page, size, Sort.by("startTime", "id")));
        return new ShowtimePage(result.getContent().stream().map(s -> response(s, priceRows(s.getId()))).toList(),
                result.getTotalElements(), result.getTotalPages(), page, size);
    }

    public ShowtimeResponse getShowtime(Long id, boolean admin) {
        Showtime showtime = showtime(id);
        List<ShowtimePrice> currentPrices = priceRows(id);
        if (!admin) requirePublic(showtime, currentPrices, roomSeats(showtime.getAuditorium().getId()));
        return response(showtime, currentPrices);
    }

    public PricedSeatMap getSeatMap(Long id) {
        Showtime showtime = showtime(id);
        Auditorium room = showtime.getAuditorium();
        List<ShowtimePrice> currentPrices = priceRows(id);
        List<Seat> layout = roomSeats(room.getId());
        requirePublic(showtime, currentPrices, layout);
        Map<Integer, BigDecimal> amounts = new HashMap<>();
        currentPrices.forEach(p -> amounts.put(p.getSeatType().getId(), p.getPrice()));
        List<PricedSeat> mapped = layout.stream().map(s -> {
            SeatType type = s.getSeatType();
            BigDecimal amount = s.getStatus() == SeatStatus.AISLE ? null : amounts.get(type.getId());
            return new PricedSeat(s.getId(), s.getRowLabel(), s.getColNumber(), s.getGridX(), s.getGridY(),
                    type.getId(), type.getTypeName(), type.getSeatSpan(), s.getStatus(), amount,
                    s.getStatus() == SeatStatus.AVAILABLE && amount != null, SeatLayoutSupport.widthOf(s));
        }).toList();
        return new PricedSeatMap(response(showtime, currentPrices), room.getTotalRows(), room.getTotalCols(), mapped,
                SeatLayoutSupport.settingsOf(room));
    }

    @Transactional
    public ShowtimeResponse createShowtime(ShowtimeRequest body) {
        Auditorium room = lockRoom(body.auditoriumId());
        Movie movie = movie(body.movieId());
        requireResources(movie, room, body.cinemaId());
        LocalDateTime end = endTime(movie, body.startTime());
        requireFuture(body.startTime());
        List<Seat> layout = roomSeats(room.getId());
        Map<Integer, SeatType> types = physicalTypes(layout);
        validatePrices(body.prices(), types);
        requireNoOverlap(room.getId(), body.startTime(), end, 0L);
        Showtime showtime = new Showtime();
        apply(showtime, movie, room, body, end);
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        showtime = showtimes.save(showtime);
        return response(showtime, savePrices(showtime, body.prices(), types, List.of()));
    }

    @Transactional
    public ShowtimeResponse updateShowtime(Long id, ShowtimeRequest body) {
        long oldRoomId = auditoriumId(id);
        Map<Long, Auditorium> lockedRooms = new HashMap<>();
        // Always lock both old/new rooms in ID order when moving a scheduled showtime.
        for (Long roomId : new TreeSet<>(List.of(oldRoomId, body.auditoriumId()))) lockedRooms.put(roomId, lockRoom(roomId));
        Showtime showtime = lockedShowtime(id, oldRoomId);
        if (showtime.getStatus() != ShowtimeStatus.SCHEDULED) throw conflict("Chỉ sửa suất đang ở trạng thái SCHEDULED");
        requireNoBookings(id);
        requireFuture(body.startTime());
        Auditorium room = lockedRooms.get(body.auditoriumId());
        Movie movie = movie(body.movieId());
        requireResources(movie, room, body.cinemaId());
        LocalDateTime end = endTime(movie, body.startTime());
        Map<Integer, SeatType> types = physicalTypes(roomSeats(room.getId()));
        validatePrices(body.prices(), types);
        requireNoOverlap(room.getId(), body.startTime(), end, id);
        List<ShowtimePrice> existing = priceRows(id);
        apply(showtime, movie, room, body, end);
        showtimes.save(showtime);
        return response(showtime, savePrices(showtime, body.prices(), types, existing));
    }

    @Transactional
    public ShowtimeResponse updateStatus(Long id, StatusRequest body) {
        long roomId = auditoriumId(id);
        lockRoom(roomId);
        Showtime showtime = lockedShowtime(id, roomId);
        ShowtimeStatus previous = showtime.getStatus(), next = body.status();
        List<ShowtimePrice> currentPrices = priceRows(id);
        if (previous == next) return response(showtime, currentPrices);
        if (previous == ShowtimeStatus.CANCELLED || previous == ShowtimeStatus.COMPLETED || next == ShowtimeStatus.SCHEDULED) {
            throw conflict("Không thể chuyển trạng thái suất chiếu theo yêu cầu");
        }
        if (next == ShowtimeStatus.OPEN_FOR_SALE) {
            if (previous != ShowtimeStatus.SCHEDULED || !showtime.getStartTime().isAfter(now())) {
                throw conflict("Chỉ mở bán suất đã lên lịch và chưa bắt đầu");
            }
            requireResources(showtime.getMovie(), showtime.getAuditorium(), showtime.getAuditorium().getCinema().getId());
            List<Seat> layout = roomSeats(roomId);
            Map<Integer, SeatType> types = physicalTypes(layout);
            List<PriceInput> inputs = currentPrices.stream().map(p -> new PriceInput(p.getSeatType().getId(), p.getPrice())).toList();
            validatePrices(inputs, types);
            if (layout.stream().noneMatch(s -> s.getStatus() == SeatStatus.AVAILABLE)) throw conflict("Phòng chưa có ghế hoạt động để mở bán");
            requireNoOverlap(roomId, showtime.getStartTime(), showtime.getEndTime(), id);
        } else if (next == ShowtimeStatus.CANCELLED) {
            requireNoBookings(id);
        } else if (next == ShowtimeStatus.COMPLETED) {
            if (now().isBefore(showtime.getEndTime())) throw conflict("Chỉ đánh dấu hoàn thành sau giờ kết thúc");
        }
        showtime.setStatus(next);
        return response(showtimes.save(showtime), currentPrices);
    }

    private void apply(Showtime showtime, Movie movie, Auditorium room, ShowtimeRequest body, LocalDateTime end) {
        showtime.setMovie(movie);
        showtime.setAuditorium(room);
        showtime.setStartTime(body.startTime());
        showtime.setEndTime(end);
        showtime.setBasePrice(body.basePrice());
    }

    private List<ShowtimePrice> savePrices(Showtime showtime, List<PriceInput> input,
                                          Map<Integer, SeatType> types, List<ShowtimePrice> existing) {
        Map<Integer, ShowtimePrice> previous = new HashMap<>();
        existing.forEach(p -> previous.put(p.getSeatType().getId(), p));
        List<ShowtimePrice> saved = new ArrayList<>();
        for (PriceInput p : input) {
            ShowtimePrice row = previous.remove(p.seatTypeId());
            if (row == null) row = new ShowtimePrice();
            row.setShowtime(showtime);
            row.setSeatType(types.get(p.seatTypeId()));
            row.setPrice(p.price());
            saved.add(row);
        }
        if (!previous.isEmpty()) prices.deleteAll(previous.values());
        return prices.saveAll(saved);
    }

    private void validatePrices(List<PriceInput> input, Map<Integer, SeatType> types) {
        Set<Integer> ids = new HashSet<>();
        for (PriceInput price : input) {
            if (!ids.add(price.seatTypeId())) throw bad("Mỗi loại ghế chỉ được có một mức giá");
            if (price.price() == null || price.price().signum() < 0 || price.price().scale() > 2
                    || price.price().compareTo(new BigDecimal("9999999999.99")) > 0) throw bad("Giá ghế không hợp lệ");
        }
        if (!ids.equals(types.keySet())) throw bad("Cần giá cho đúng tất cả loại ghế trong phòng, không tính lối đi");
    }

    private Map<Integer, SeatType> physicalTypes(List<Seat> layout) {
        Map<Integer, SeatType> types = new HashMap<>();
        layout.stream().filter(s -> s.getStatus() != SeatStatus.AISLE)
                .forEach(s -> types.put(s.getSeatType().getId(), s.getSeatType()));
        if (types.isEmpty()) throw bad("Phòng chưa có sơ đồ ghế hợp lệ");
        return types;
    }

    private void requireResources(Movie movie, Auditorium room, Long cinemaId) {
        if (!room.getCinema().getId().equals(cinemaId)) throw bad("Phòng không thuộc rạp đã chọn");
        if (!eligibleMovie(movie) || room.getStatus() != AuditoriumStatus.ACTIVE
                || room.getCinema().getStatus() != CinemaStatus.ACTIVE) {
            throw conflict("Cần phim sắp/đang chiếu và rạp, phòng đang hoạt động");
        }
    }

    private void requirePublic(Showtime showtime, List<ShowtimePrice> currentPrices, List<Seat> layout) {
        Auditorium room = showtime.getAuditorium();
        Set<Integer> pricedTypes = new HashSet<>();
        currentPrices.stream().filter(p -> p.getPrice() != null && p.getPrice().signum() >= 0)
                .forEach(p -> pricedTypes.add(p.getSeatType().getId()));
        boolean complete = layout.stream().filter(s -> s.getStatus() != SeatStatus.AISLE)
                .allMatch(s -> pricedTypes.contains(s.getSeatType().getId()));
        if (showtime.getStatus() != ShowtimeStatus.OPEN_FOR_SALE || !showtime.getStartTime().isAfter(now())
                || !eligibleMovie(showtime.getMovie()) || room.getStatus() != AuditoriumStatus.ACTIVE
                || room.getCinema().getStatus() != CinemaStatus.ACTIVE || !complete
                || layout.stream().noneMatch(s -> s.getStatus() == SeatStatus.AVAILABLE)) {
            throw missing("Không tìm thấy suất chiếu đang mở bán");
        }
    }

    private boolean eligibleMovie(Movie movie) {
        return movie.getStatus() == MovieStatus.COMING_SOON || movie.getStatus() == MovieStatus.NOW_SHOWING;
    }
    private LocalDateTime endTime(Movie movie, LocalDateTime start) {
        Integer duration = movie.getDurationMinutes();
        if (duration == null || duration < 1 || duration > 600) throw bad("Thời lượng phim không hợp lệ");
        try {
            LocalDateTime end = start.plusMinutes(duration);
            if (start.getYear() > 9999 || end.getYear() > 9999) throw bad("Ngày giờ chiếu không hợp lệ");
            return end;
        } catch (DateTimeException ex) { throw bad("Ngày giờ chiếu không hợp lệ"); }
    }
    private void requireFuture(LocalDateTime start) { if (!start.isAfter(now())) throw bad("Giờ bắt đầu phải ở tương lai (giờ Việt Nam)"); }
    private void requireNoOverlap(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (showtimes.hasOverlap(roomId, start, end, excludeId)) throw conflict("Lịch chiếu bị chồng với suất khác trong cùng phòng");
    }
    private void requireNoBookings(Long id) {
        if (tickets.existsByShowtimeId(id) || holds.existsByShowtimeIdAndStatusAndExpiresAtAfter(id, HoldStatus.HOLDING, now())) {
            throw conflict("Suất đã có vé hoặc ghế đang giữ; cần xử lý đặt vé trước khi sửa/hủy");
        }
    }
    private long auditoriumId(Long id) {
        return showtimes.findAuditoriumIdById(id).orElseThrow(() -> missing("Không tìm thấy suất chiếu"));
    }
    private Showtime lockedShowtime(Long id, long expectedRoomId) {
        Showtime showtime = showtimes.findByIdForUpdate(id).orElseThrow(() -> missing("Không tìm thấy suất chiếu"));
        if (showtime.getAuditorium().getId() != expectedRoomId) throw conflict("Suất vừa được chuyển phòng; vui lòng tải lại");
        return showtime;
    }
    private Auditorium lockRoom(Long id) {
        return auditoriums.findByIdForUpdate(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
    }
    private Showtime showtime(Long id) { return showtimes.findById(id).orElseThrow(() -> missing("Không tìm thấy suất chiếu")); }
    private Movie movie(Long id) { return movies.findById(id).orElseThrow(() -> missing("Không tìm thấy phim")); }
    private List<ShowtimePrice> priceRows(Long id) { return prices.findByShowtimeIdOrderBySeatTypeIdAsc(id); }
    private List<Seat> roomSeats(Long id) { return seats.findByAuditoriumIdOrderByGridYAscGridXAsc(id); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private ShowtimeResponse response(Showtime s, List<ShowtimePrice> rows) {
        Auditorium room = s.getAuditorium();
        Movie movie = s.getMovie();
        int scheduledDuration = Math.toIntExact(Duration.between(s.getStartTime(), s.getEndTime()).toMinutes());
        List<PriceResponse> mapped = rows.stream().sorted(Comparator.comparing(p -> p.getSeatType().getId()))
                .map(p -> new PriceResponse(p.getSeatType().getId(), p.getSeatType().getTypeName(), p.getSeatType().getSeatSpan(), p.getPrice())).toList();
        return new ShowtimeResponse(s.getId(), movie.getId(), movie.getTitle(), movie.getPosterUrl(), scheduledDuration,
                movie.getAgeRating(), room.getCinema().getId(), room.getCinema().getCinemaName(), room.getId(), room.getName(),
                s.getStartTime(), s.getEndTime(), s.getBasePrice(), s.getStatus(), mapped);
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
