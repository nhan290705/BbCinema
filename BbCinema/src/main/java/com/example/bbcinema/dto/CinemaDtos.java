package com.example.bbcinema.dto;

import com.example.bbcinema.enums.AuditoriumStatus;
import com.example.bbcinema.enums.CinemaStatus;
import com.example.bbcinema.enums.SeatStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CinemaDtos {
    private CinemaDtos() {}

    public record ProvinceRequest(
            @NotBlank @Size(max = 100) String provinceName,
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,20}") String code) {
        public ProvinceRequest {
            provinceName = trim(provinceName);
            code = upper(code);
        }
    }

    public record ProvinceResponse(Integer id, String provinceName, String code) {}

    public record CinemaRequest(
            @NotNull @Positive Integer provinceId,
            @NotBlank @Size(max = 150) String cinemaName,
            @NotBlank @Size(max = 255) String address,
            @Size(max = 20) String hotline,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @NotNull CinemaStatus status) {
        public CinemaRequest {
            cinemaName = trim(cinemaName);
            address = trim(address);
            hotline = trim(hotline);
        }
    }

    public record CinemaResponse(Long id, Integer provinceId, String provinceName, String cinemaName,
                                 String address, String hotline, BigDecimal latitude, BigDecimal longitude,
                                 CinemaStatus status) {}

    public record AuditoriumRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(1) @Max(100) Integer totalRows,
            @NotNull @Min(1) @Max(100) Integer totalCols,
            @NotNull AuditoriumStatus status) {
        public AuditoriumRequest { name = trim(name); }
    }

    public record AuditoriumResponse(Long id, Long cinemaId, String name, Integer totalRows,
                                     Integer totalCols, AuditoriumStatus status) {}

    public record AuditoriumStatusRequest(@NotNull AuditoriumStatus status) {}

    public record SeatTypeRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String typeName,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal defaultSurcharge,
            @NotNull @Min(1) @Max(2) Integer seatSpan) {
        public SeatTypeRequest { typeName = upper(typeName); }
    }

    public record SeatTypeResponse(Integer id, String typeName, BigDecimal defaultSurcharge, Integer seatSpan) {}

    public record GenerateSeatsRequest(
            @NotNull @Positive Integer defaultSeatTypeId,
            @Size(max = 100) Map<@Pattern(regexp = "[A-Z]{1,5}") String, @NotNull @Positive Integer> rowTypes) {}

    public record SeatInput(
            @NotBlank @Pattern(regexp = "[A-Z]{1,5}") String rowLabel,
            @NotNull @Positive Integer colNumber,
            @NotNull @Min(0) Integer gridX,
            @NotNull @Min(0) Integer gridY,
            @NotNull @Positive Integer seatTypeId,
            @NotNull SeatStatus status) {
        public SeatInput { rowLabel = upper(rowLabel); }
    }

    public record SeatLayoutRequest(@NotEmpty @Size(max = 5000) List<@NotNull @Valid SeatInput> seats) {}
    public record SeatStatusRequest(@NotNull SeatStatus status) {}

    public record SeatResponse(Long id, String rowLabel, Integer colNumber, Integer gridX, Integer gridY,
                               SeatStatus status, Integer seatTypeId, String seatTypeName,
                               Integer seatSpan, BigDecimal defaultSurcharge, Integer widthUnits) {
        public SeatResponse(Long id, String rowLabel, Integer colNumber, Integer gridX, Integer gridY,
                            SeatStatus status, Integer seatTypeId, String seatTypeName, Integer seatSpan, BigDecimal defaultSurcharge) {
            this(id, rowLabel, colNumber, gridX, gridY, status, seatTypeId, seatTypeName, seatSpan, defaultSurcharge, seatSpan);
        }
    }

    public record SeatMapResponse(Long auditoriumId, Long cinemaId, String cinemaName, String auditoriumName,
                                  Integer totalRows, Integer totalCols, AuditoriumStatus status,
                                  List<SeatResponse> seats, SeatLayoutDtos.Settings settings) {
        public SeatMapResponse(Long auditoriumId, Long cinemaId, String cinemaName, String auditoriumName,
                               Integer totalRows, Integer totalCols, AuditoriumStatus status, List<SeatResponse> seats) {
            this(auditoriumId, cinemaId, cinemaName, auditoriumName, totalRows, totalCols, status, seats,
                    new SeatLayoutDtos.Settings(38, 36, 7, 7, "TOP"));
        }
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
