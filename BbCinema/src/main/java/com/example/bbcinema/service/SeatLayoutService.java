package com.example.bbcinema.service;

import com.example.bbcinema.dto.SeatLayoutDtos.*;
import com.example.bbcinema.enums.AuditoriumStatus;
import com.example.bbcinema.enums.SeatStatus;
import com.example.bbcinema.enums.ShowtimeStatus;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SeatLayoutService {
    private final AuditoriumRepository auditoriums;
    private final CinemaRepository cinemas;
    private final SeatRepository seats;
    private final SeatTypeRepository types;
    private final ShowtimeRepository showtimes;
    private final TicketRepository tickets;
    private final SeatHoldRepository holds;
    private final Clock clock;

    public SeatLayoutService(AuditoriumRepository auditoriums, CinemaRepository cinemas, SeatRepository seats,
                              SeatTypeRepository types, ShowtimeRepository showtimes, TicketRepository tickets,
                              SeatHoldRepository holds, Clock clock) {
        this.auditoriums = auditoriums;
        this.cinemas = cinemas;
        this.seats = seats;
        this.types = types;
        this.showtimes = showtimes;
        this.tickets = tickets;
        this.holds = holds;
        this.clock = clock;
    }

    public LayoutResponse getLayout(Long id) {
        Auditorium room = auditoriums.findById(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
        return response(room, roomSeats(id));
    }

    @Transactional
    public LayoutResponse saveLayout(Long id, SaveLayoutRequest body) {
        Auditorium room = lockedRoom(id);
        List<Seat> existing = roomSeats(id);
        if (!revision(room, existing).equals(body.revision())) {
            throw conflict("Sơ đồ đã thay đổi từ lần tải trước. Vui lòng tải lại trước khi lưu");
        }
        Map<Long, Seat> previous = new HashMap<>();
        existing.forEach(s -> previous.put(s.getId(), s));
        Map<Integer, SeatType> selectedTypes = validate(body, previous);
        boolean structural = structuralChange(room, existing, body);
        String restriction = structural ? lockReason(id) : null;
        if (restriction != null) throw conflict(restriction);

        Set<Long> retainedIds = new HashSet<>();
        body.seats().stream().map(SeatInput::id).filter(Objects::nonNull).forEach(retainedIds::add);
        List<Seat> removed = existing.stream().filter(s -> !retainedIds.contains(s.getId())).toList();
        if (!removed.isEmpty()) {
            seats.deleteAll(removed);
            seats.flush();
        }

        boolean renumbering = body.seats().stream().filter(s -> s.id() != null).anyMatch(s -> {
            Seat original = previous.get(s.id());
            return !Objects.equals(original.getRowLabel(), s.rowLabel()) || !Objects.equals(original.getColNumber(), s.colNumber());
        });
        if (renumbering) {
            // Free every retained label before swaps such as A1 <-> A2. The temporary labels are not visible outside this transaction.
            List<Seat> retained = existing.stream().filter(s -> retainedIds.contains(s.getId())).toList();
            for (int index = 0; index < retained.size(); index++) {
                retained.get(index).setRowLabel("~");
                retained.get(index).setColNumber(index + 1);
            }
            seats.saveAll(retained);
            seats.flush();
        }

        List<Seat> updated = new ArrayList<>();
        for (SeatInput input : body.seats()) {
            Seat seat = input.id() == null ? new Seat() : previous.get(input.id());
            seat.setAuditorium(room);
            seat.setRowLabel(input.rowLabel());
            seat.setColNumber(input.colNumber());
            seat.setGridX(input.gridX());
            seat.setGridY(input.gridY());
            seat.setSeatType(selectedTypes.get(input.seatTypeId()));
            seat.setWidthUnits(input.widthUnits());
            seat.setStatus(input.status());
            updated.add(seat);
        }
        room.setTotalRows(body.totalRows());
        room.setTotalCols(body.totalCols());
        SeatLayoutSupport.applySettings(room, body.settings());
        auditoriums.save(room);
        List<Seat> saved = seats.saveAll(updated);
        seats.flush();
        return response(room, saved);
    }

    @Transactional
    public LayoutResponse copyLayout(Long id, CopyLayoutRequest body) {
        Auditorium source = lockedRoom(id);
        List<Seat> original = roomSeats(id);
        Cinema target = cinemas.findById(body.cinemaId()).orElseThrow(() -> missing("Không tìm thấy rạp đích"));
        if (auditoriums.existsByCinemaIdAndNameIgnoreCase(target.getId(), body.name())) {
            throw conflict("Tên phòng đã tồn tại trong rạp đích");
        }
        Auditorium copy = new Auditorium();
        copy.setCinema(target);
        copy.setName(body.name());
        copy.setTotalRows(source.getTotalRows());
        copy.setTotalCols(source.getTotalCols());
        copy.setStatus(AuditoriumStatus.ACTIVE);
        SeatLayoutSupport.applySettings(copy, SeatLayoutSupport.settingsOf(source));
        copy = auditoriums.save(copy);
        List<Seat> cloned = new ArrayList<>();
        for (Seat seat : original) {
            Seat next = new Seat();
            next.setAuditorium(copy);
            next.setRowLabel(seat.getRowLabel());
            next.setColNumber(seat.getColNumber());
            next.setGridX(seat.getGridX());
            next.setGridY(seat.getGridY());
            next.setSeatType(seat.getSeatType());
            next.setWidthUnits(SeatLayoutSupport.widthOf(seat));
            next.setStatus(seat.getStatus() == SeatStatus.AISLE ? SeatStatus.AISLE : SeatStatus.AVAILABLE);
            cloned.add(next);
        }
        List<Seat> saved = seats.saveAll(cloned);
        seats.flush();
        return response(copy, saved);
    }

    private Map<Integer, SeatType> validate(SaveLayoutRequest body, Map<Long, Seat> previous) {
        if (body.totalRows() * body.totalCols() > 5000) throw bad("Sơ đồ tối đa 5000 ô");
        Map<Integer, SeatType> selectedTypes = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        Set<String> labels = new HashSet<>();
        Set<String> occupied = new HashSet<>();
        Map<Integer, String> rowLabels = new HashMap<>();
        Map<String, Integer> labelRows = new HashMap<>();
        for (SeatInput input : body.seats()) {
            if (input.id() != null) {
                if (!ids.add(input.id())) throw bad("Một ghế bị gửi nhiều lần");
                if (!previous.containsKey(input.id())) throw missing("Ghế không thuộc phòng đang chỉnh sửa");
            }
            SeatType type = selectedTypes.computeIfAbsent(input.seatTypeId(), this::seatType);
            if (type.getSeatSpan() == null || type.getSeatSpan() < 1 || type.getSeatSpan() > 2
                    || input.widthUnits() < type.getSeatSpan()) {
                throw bad("Chiều rộng ghế phải đủ số ô của loại ghế; ghế đôi cần ít nhất 2 ô");
            }
            if (input.gridY() >= body.totalRows() || input.gridX() > body.totalCols() - input.widthUnits()) {
                throw bad("Ghế nằm ngoài kích thước phòng");
            }
            String previousLabel = rowLabels.putIfAbsent(input.gridY(), input.rowLabel());
            Integer previousRow = labelRows.putIfAbsent(input.rowLabel(), input.gridY());
            if ((previousLabel != null && !previousLabel.equals(input.rowLabel()))
                    || (previousRow != null && !previousRow.equals(input.gridY()))) {
                throw bad("Mỗi hàng cần một nhãn riêng và thống nhất trên toàn hàng");
            }
            if (!labels.add(input.rowLabel() + ":" + input.colNumber())) throw bad("Trùng mã ghế trong cùng hàng");
            for (int x = input.gridX(); x < input.gridX() + input.widthUnits(); x++) {
                if (!occupied.add(input.gridY() + ":" + x)) throw bad("Các ghế bị chồng lên nhau");
            }
        }
        return selectedTypes;
    }

    private boolean structuralChange(Auditorium room, List<Seat> existing, SaveLayoutRequest body) {
        if (!Objects.equals(room.getTotalRows(), body.totalRows()) || !Objects.equals(room.getTotalCols(), body.totalCols())
                || existing.size() != body.seats().size()) return true;
        Map<Long, Seat> previous = new HashMap<>();
        existing.forEach(s -> previous.put(s.getId(), s));
        for (SeatInput input : body.seats()) {
            Seat old = input.id() == null ? null : previous.get(input.id());
            if (old == null || !Objects.equals(old.getRowLabel(), input.rowLabel())
                    || !Objects.equals(old.getColNumber(), input.colNumber()) || !Objects.equals(old.getGridX(), input.gridX())
                    || !Objects.equals(old.getGridY(), input.gridY()) || !Objects.equals(old.getSeatType().getId(), input.seatTypeId())
                    || SeatLayoutSupport.widthOf(old) != input.widthUnits()
                    || ((old.getStatus() == SeatStatus.AISLE) != (input.status() == SeatStatus.AISLE))) return true;
        }
        return false;
    }

    private String lockReason(Long id) {
        if (tickets.existsBySeatAuditoriumId(id)) return "Phòng đã có vé trong lịch sử. Hãy nhân bản phòng để thiết kế bố trí mới";
        if (holds.existsBySeatAuditoriumId(id)) return "Phòng đã có dữ liệu giữ ghế. Hãy nhân bản phòng để giữ nguyên lịch sử";
        if (showtimes.existsByAuditoriumIdAndStatusInAndEndTimeAfter(id,
                List.of(ShowtimeStatus.SCHEDULED, ShowtimeStatus.OPEN_FOR_SALE), LocalDateTime.now(clock))) {
            return "Phòng đang có suất chưa kết thúc. Hãy hủy lịch chưa có đặt vé hoặc nhân bản phòng để đổi bố trí";
        }
        return null;
    }

    private LayoutResponse response(Auditorium room, List<Seat> layout) {
        String reason = lockReason(room.getId());
        List<LayoutSeat> ordered = layout.stream().sorted(Comparator.comparing(Seat::getGridY).thenComparing(Seat::getGridX))
                .map(s -> new LayoutSeat(s.getId(), s.getRowLabel(), s.getColNumber(), s.getGridX(), s.getGridY(),
                        s.getSeatType().getId(), s.getSeatType().getTypeName(), s.getSeatType().getSeatSpan(), SeatLayoutSupport.widthOf(s),
                        s.getStatus(), s.getSeatType().getDefaultSurcharge())).toList();
        return new LayoutResponse(room.getId(), room.getCinema().getId(), room.getCinema().getCinemaName(), room.getName(),
                room.getTotalRows(), room.getTotalCols(), room.getStatus(), revision(room, layout), reason == null, reason,
                SeatLayoutSupport.settingsOf(room), ordered);
    }

    private String revision(Auditorium room, List<Seat> layout) {
        StringBuilder canonical = new StringBuilder().append(room.getId()).append('|').append(room.getTotalRows()).append('|')
                .append(room.getTotalCols()).append('|').append(room.getStatus()).append('|').append(SeatLayoutSupport.settingsOf(room));
        layout.stream().sorted(Comparator.comparing(Seat::getId)).forEach(s -> canonical.append(';').append(s.getId()).append('|')
                .append(s.getRowLabel()).append('|').append(s.getColNumber()).append('|').append(s.getGridX()).append('|')
                .append(s.getGridY()).append('|').append(s.getSeatType().getId()).append('|').append(SeatLayoutSupport.widthOf(s)).append('|').append(s.getStatus()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private Auditorium lockedRoom(Long id) {
        return auditoriums.findByIdForUpdate(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
    }
    private List<Seat> roomSeats(Long id) { return seats.findByAuditoriumIdOrderByGridYAscGridXAsc(id); }
    private SeatType seatType(Integer id) { return types.findById(id).orElseThrow(() -> missing("Không tìm thấy loại ghế")); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
