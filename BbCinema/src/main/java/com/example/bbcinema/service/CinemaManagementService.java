package com.example.bbcinema.service;

import com.example.bbcinema.dto.CinemaDtos.*;
import com.example.bbcinema.enums.SeatStatus;
import com.example.bbcinema.model.cinema.*;
import com.example.bbcinema.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class CinemaManagementService {
    private final ProvinceRepository provinces;
    private final CinemaRepository cinemas;
    private final AuditoriumRepository auditoriums;
    private final SeatTypeRepository seatTypes;
    private final SeatRepository seats;

    public CinemaManagementService(ProvinceRepository provinces, CinemaRepository cinemas,
                                   AuditoriumRepository auditoriums, SeatTypeRepository seatTypes,
                                   SeatRepository seats) {
        this.provinces = provinces;
        this.cinemas = cinemas;
        this.auditoriums = auditoriums;
        this.seatTypes = seatTypes;
        this.seats = seats;
    }

    public List<ProvinceResponse> listProvinces() {
        return provinces.findAll(Sort.by("provinceName", "id")).stream().map(this::provinceResponse).toList();
    }

    @Transactional
    public ProvinceResponse createProvince(ProvinceRequest body) {
        if (provinces.existsByCodeIgnoreCase(body.code())) throw conflict("Mã tỉnh/thành đã tồn tại");
        Province province = new Province();
        province.setProvinceName(body.provinceName());
        province.setCode(body.code());
        return provinceResponse(provinces.save(province));
    }

    public List<CinemaResponse> listCinemas(Integer provinceId) {
        if (provinceId != null) province(provinceId);
        List<Cinema> result = provinceId == null ? cinemas.findAll(Sort.by("cinemaName", "id"))
                : cinemas.findByProvinceIdOrderByCinemaNameAscIdAsc(provinceId);
        return result.stream().map(this::cinemaResponse).toList();
    }

    public CinemaResponse getCinema(Long id) { return cinemaResponse(cinema(id)); }

    @Transactional
    public CinemaResponse createCinema(CinemaRequest body) {
        return cinemaResponse(cinemas.save(applyCinema(new Cinema(), body)));
    }

    @Transactional
    public CinemaResponse updateCinema(Long id, CinemaRequest body) {
        return cinemaResponse(cinemas.save(applyCinema(cinema(id), body)));
    }

    private Cinema applyCinema(Cinema cinema, CinemaRequest body) {
        cinema.setProvince(province(body.provinceId()));
        cinema.setCinemaName(body.cinemaName());
        cinema.setAddress(body.address());
        cinema.setHotline(body.hotline());
        cinema.setLatitude(body.latitude());
        cinema.setLongitude(body.longitude());
        cinema.setStatus(body.status());
        return cinema;
    }

    public List<AuditoriumResponse> listAuditoriums(Long cinemaId) {
        cinema(cinemaId);
        return auditoriums.findByCinemaIdOrderByNameAscIdAsc(cinemaId).stream().map(this::auditoriumResponse).toList();
    }

    @Transactional
    public AuditoriumResponse createAuditorium(Long cinemaId, AuditoriumRequest body) {
        Cinema cinema = cinema(cinemaId);
        if (body.totalRows() * body.totalCols() > 5000) throw bad("Phòng tối đa 5000 ô trong sơ đồ");
        if (auditoriums.existsByCinemaIdAndNameIgnoreCase(cinemaId, body.name())) {
            throw conflict("Tên phòng đã tồn tại trong rạp");
        }
        Auditorium room = new Auditorium();
        room.setCinema(cinema);
        room.setName(body.name());
        room.setTotalRows(body.totalRows());
        room.setTotalCols(body.totalCols());
        room.setStatus(body.status());
        return auditoriumResponse(auditoriums.save(room));
    }

    @Transactional
    public AuditoriumResponse updateAuditoriumStatus(Long id, AuditoriumStatusRequest body) {
        Auditorium room = auditoriums.findByIdForUpdate(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
        room.setStatus(body.status());
        return auditoriumResponse(auditoriums.save(room));
    }

    public List<SeatTypeResponse> listSeatTypes() {
        return seatTypes.findAll(Sort.by("id")).stream().map(this::seatTypeResponse).toList();
    }

    @Transactional
    public SeatTypeResponse createSeatType(SeatTypeRequest body) {
        if ((body.typeName().equals("COUPLE") && body.seatSpan() != 2)
                || (Set.of("STANDARD", "VIP").contains(body.typeName()) && body.seatSpan() != 1)) {
            throw bad("Ghế thường/VIP chiếm 1 ô; ghế đôi COUPLE chiếm 2 ô");
        }
        if (seatTypes.existsByTypeNameIgnoreCase(body.typeName())) throw conflict("Loại ghế đã tồn tại");
        SeatType type = new SeatType();
        type.setTypeName(body.typeName());
        type.setDefaultSurcharge(body.defaultSurcharge());
        type.setSeatSpan(body.seatSpan());
        return seatTypeResponse(seatTypes.save(type));
    }

    @Transactional
    public SeatMapResponse generateSeats(Long auditoriumId, GenerateSeatsRequest body) {
        Auditorium room = emptyLockedRoom(auditoriumId);
        SeatType defaultType = seatType(body.defaultSeatTypeId());
        Map<String, SeatType> rowTypes = new HashMap<>();
        Set<String> validRows = new HashSet<>();
        for (int y = 0; y < room.getTotalRows(); y++) validRows.add(rowLabel(y));
        if (body.rowTypes() != null) {
            body.rowTypes().forEach((label, typeId) -> {
                if (!validRows.contains(label)) throw bad("Hàng " + label + " nằm ngoài phòng");
                rowTypes.put(label, seatType(typeId));
            });
        }
        List<Seat> generated = new ArrayList<>();
        for (int y = 0; y < room.getTotalRows(); y++) {
            String label = rowLabel(y);
            SeatType type = rowTypes.getOrDefault(label, defaultType);
            int span = span(type);
            if (room.getTotalCols() % span != 0) {
                throw bad("Hàng " + label + " không đủ cột để xếp ghế đôi; dùng sơ đồ tùy chỉnh để chừa lối đi");
            }
            for (int x = 0; x < room.getTotalCols(); x += span) {
                generated.add(newSeat(room, type, label, x + 1, x, y, SeatStatus.AVAILABLE));
            }
        }
        return seatMapResponse(room, seats.saveAll(generated));
    }

    @Transactional
    public SeatMapResponse createLayout(Long auditoriumId, SeatLayoutRequest body) {
        Auditorium room = emptyLockedRoom(auditoriumId);
        Set<String> labels = new HashSet<>();
        Set<String> occupied = new HashSet<>();
        Map<Integer, SeatType> types = new HashMap<>();
        List<Seat> layout = new ArrayList<>();
        for (SeatInput input : body.seats()) {
            SeatType type = types.computeIfAbsent(input.seatTypeId(), this::seatType);
            int span = span(type);
            int x = input.gridX(), y = input.gridY();
            // Subtract before comparing so even an Integer.MAX_VALUE input cannot overflow.
            if (y >= room.getTotalRows() || x > room.getTotalCols() - span) {
                throw bad("Ghế nằm ngoài kích thước phòng");
            }
            if (!rowLabel(y).equals(input.rowLabel()) || input.colNumber() != x + 1) {
                throw bad("Nhãn hàng/cột phải khớp tọa độ: hàng A bắt đầu tại gridY=0, colNumber=gridX+1");
            }
            if (!labels.add(input.rowLabel() + ":" + input.colNumber())) throw bad("Trùng vị trí ghế");
            for (int col = x; col < x + span; col++) {
                if (!occupied.add(y + ":" + col)) throw bad("Các ghế bị chồng lên nhau");
            }
            layout.add(newSeat(room, type, input.rowLabel(), input.colNumber(), x, y, input.status()));
        }
        return seatMapResponse(room, seats.saveAll(layout));
    }

    public SeatMapResponse getSeatMap(Long auditoriumId) {
        Auditorium room = auditorium(auditoriumId);
        return seatMapResponse(room, seats.findByAuditoriumIdOrderByGridYAscGridXAsc(auditoriumId));
    }

    @Transactional
    public SeatResponse updateSeatStatus(Long auditoriumId, Long seatId, SeatStatusRequest body) {
        auditoriums.findByIdForUpdate(auditoriumId).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
        Seat seat = seats.findByIdAndAuditoriumId(seatId, auditoriumId)
                .orElseThrow(() -> missing("Không tìm thấy ghế trong phòng này"));
        seat.setStatus(body.status());
        return seatResponse(seats.save(seat));
    }

    private Auditorium emptyLockedRoom(Long id) {
        Auditorium room = auditoriums.findByIdForUpdate(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
        if (seats.existsByAuditoriumId(id)) {
            throw conflict("Phòng đã có sơ đồ ghế. Chỉ cập nhật trạng thái để giữ nguyên ID ghế và dữ liệu đặt vé");
        }
        if (room.getTotalRows() == null || room.getTotalCols() == null || room.getTotalRows() < 1
                || room.getTotalCols() < 1 || room.getTotalRows() > 100 || room.getTotalCols() > 100
                || room.getTotalRows() * room.getTotalCols() > 5000) {
            throw bad("Kích thước phòng không hợp lệ: tối đa 100 hàng, 100 cột và 5000 ô");
        }
        return room;
    }

    private Province province(Integer id) {
        return provinces.findById(id).orElseThrow(() -> missing("Không tìm thấy tỉnh/thành"));
    }
    private Cinema cinema(Long id) {
        return cinemas.findById(id).orElseThrow(() -> missing("Không tìm thấy rạp"));
    }
    private Auditorium auditorium(Long id) {
        return auditoriums.findById(id).orElseThrow(() -> missing("Không tìm thấy phòng chiếu"));
    }
    private SeatType seatType(Integer id) {
        return seatTypes.findById(id).orElseThrow(() -> missing("Không tìm thấy loại ghế"));
    }
    private int span(SeatType type) {
        if (type.getSeatSpan() == null || type.getSeatSpan() < 1 || type.getSeatSpan() > 2) {
            throw bad("Loại ghế phải chiếm 1 hoặc 2 ô");
        }
        return type.getSeatSpan();
    }
    private Seat newSeat(Auditorium room, SeatType type, String row, int col, int x, int y, SeatStatus status) {
        Seat seat = new Seat();
        seat.setAuditorium(room);
        seat.setSeatType(type);
        seat.setRowLabel(row);
        seat.setColNumber(col);
        seat.setGridX(x);
        seat.setGridY(y);
        seat.setStatus(status);
        return seat;
    }
    private ProvinceResponse provinceResponse(Province p) {
        return new ProvinceResponse(p.getId(), p.getProvinceName(), p.getCode());
    }
    private CinemaResponse cinemaResponse(Cinema c) {
        return new CinemaResponse(c.getId(), c.getProvince().getId(), c.getProvince().getProvinceName(),
                c.getCinemaName(), c.getAddress(), c.getHotline(), c.getLatitude(), c.getLongitude(), c.getStatus());
    }
    private AuditoriumResponse auditoriumResponse(Auditorium a) {
        return new AuditoriumResponse(a.getId(), a.getCinema().getId(), a.getName(), a.getTotalRows(), a.getTotalCols(), a.getStatus());
    }
    private SeatTypeResponse seatTypeResponse(SeatType t) {
        return new SeatTypeResponse(t.getId(), t.getTypeName(), t.getDefaultSurcharge(), t.getSeatSpan());
    }
    private SeatResponse seatResponse(Seat s) {
        SeatType t = s.getSeatType();
        return new SeatResponse(s.getId(), s.getRowLabel(), s.getColNumber(), s.getGridX(), s.getGridY(),
                s.getStatus(), t.getId(), t.getTypeName(), t.getSeatSpan(), t.getDefaultSurcharge(), SeatLayoutSupport.widthOf(s));
    }
    private SeatMapResponse seatMapResponse(Auditorium a, List<Seat> result) {
        List<SeatResponse> ordered = result.stream()
                .sorted(Comparator.comparing(Seat::getGridY).thenComparing(Seat::getGridX)).map(this::seatResponse).toList();
        return new SeatMapResponse(a.getId(), a.getCinema().getId(), a.getCinema().getCinemaName(), a.getName(),
                a.getTotalRows(), a.getTotalCols(), a.getStatus(), ordered, SeatLayoutSupport.settingsOf(a));
    }
    private static String rowLabel(int index) {
        StringBuilder result = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) result.append((char) ('A' + (n - 1) % 26));
        return result.reverse().toString();
    }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
