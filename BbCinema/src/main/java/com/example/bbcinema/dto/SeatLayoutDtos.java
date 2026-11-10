package com.example.bbcinema.dto;

import com.example.bbcinema.enums.AuditoriumStatus;
import com.example.bbcinema.enums.SeatStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class SeatLayoutDtos {
    private SeatLayoutDtos() {}

    public record Settings(
            @NotNull @Min(24) @Max(80) Integer seatWidth,
            @NotNull @Min(24) @Max(64) Integer seatHeight,
            @NotNull @Min(0) @Max(40) Integer columnGap,
            @NotNull @Min(0) @Max(60) Integer rowGap,
            @NotBlank @Pattern(regexp = "TOP|BOTTOM") String screenPosition) {}

    public record SeatInput(
            @Positive Long id,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,5}") String rowLabel,
            @NotNull @Min(1) @Max(999) Integer colNumber,
            @NotNull @Min(0) Integer gridX,
            @NotNull @Min(0) Integer gridY,
            @NotNull @Positive Integer seatTypeId,
            @NotNull @Min(1) @Max(4) Integer widthUnits,
            @NotNull SeatStatus status) {
        public SeatInput { if (rowLabel != null) rowLabel = rowLabel.trim().toUpperCase(Locale.ROOT); }
    }

    public record SaveLayoutRequest(
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String revision,
            @NotNull @Min(1) @Max(100) Integer totalRows,
            @NotNull @Min(1) @Max(100) Integer totalCols,
            @NotNull @Valid Settings settings,
            @NotNull @Size(max = 5000) List<@NotNull @Valid SeatInput> seats) {}

    public record CopyLayoutRequest(@NotNull @Positive Long cinemaId, @NotBlank @Size(max = 100) String name) {
        public CopyLayoutRequest { if (name != null) name = name.trim(); }
    }

    public record LayoutSeat(Long id, String rowLabel, Integer colNumber, Integer gridX, Integer gridY,
                              Integer seatTypeId, String seatTypeName, Integer seatSpan, Integer widthUnits,
                              SeatStatus status, BigDecimal defaultSurcharge) {}

    public record LayoutResponse(Long auditoriumId, Long cinemaId, String cinemaName, String auditoriumName,
                                   Integer totalRows, Integer totalCols, AuditoriumStatus status, String revision,
                                   boolean structureEditable, String lockReason, Settings settings, List<LayoutSeat> seats) {}
}
