package com.example.bbcinema.dto;

import com.example.bbcinema.enums.SeatStatus;
import com.example.bbcinema.enums.ShowtimeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ShowtimeDtos {
    private ShowtimeDtos() {}

    public record PriceInput(@NotNull @Positive Integer seatTypeId,
                             @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) {}

    public record ShowtimeRequest(
            @NotNull @Positive Long movieId,
            @NotNull @Positive Long cinemaId,
            @NotNull @Positive Long auditoriumId,
            @NotNull LocalDateTime startTime,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid PriceInput> prices) {}

    public record StatusRequest(@NotNull ShowtimeStatus status) {}
    public record PriceResponse(Integer seatTypeId, String seatTypeName, Integer seatSpan, BigDecimal price) {}
    public record CinemaOption(Long id, String cinemaName) {}

    public record ShowtimeResponse(Long id, Long movieId, String movieTitle, String posterUrl,
                                    Integer durationMinutes, String ageRating, Long cinemaId, String cinemaName,
                                    Long auditoriumId, String auditoriumName, LocalDateTime startTime,
                                    LocalDateTime endTime, BigDecimal basePrice, ShowtimeStatus status,
                                    List<PriceResponse> prices) {}

    public record ShowtimePage(List<ShowtimeResponse> items, long totalElements, int totalPages, int page, int size) {}
    public record PricedSeat(Long seatId, String rowLabel, Integer colNumber, Integer gridX, Integer gridY,
                              Integer seatTypeId, String seatTypeName, Integer seatSpan, SeatStatus status,
                              BigDecimal price, boolean usable, Integer widthUnits) {
        public PricedSeat(Long seatId, String rowLabel, Integer colNumber, Integer gridX, Integer gridY,
                          Integer seatTypeId, String seatTypeName, Integer seatSpan, SeatStatus status, BigDecimal price, boolean usable) {
            this(seatId, rowLabel, colNumber, gridX, gridY, seatTypeId, seatTypeName, seatSpan, status, price, usable, seatSpan);
        }
    }
    public record PricedSeatMap(ShowtimeResponse showtime, Integer totalRows, Integer totalCols, List<PricedSeat> seats,
                                 SeatLayoutDtos.Settings settings) {
        public PricedSeatMap(ShowtimeResponse showtime, Integer totalRows, Integer totalCols, List<PricedSeat> seats) {
            this(showtime, totalRows, totalCols, seats, new SeatLayoutDtos.Settings(38, 36, 7, 7, "TOP"));
        }
    }
}
