package com.example.bbcinema.service;

import com.example.bbcinema.dto.SeatLayoutDtos.Settings;
import com.example.bbcinema.model.cinema.Auditorium;
import com.example.bbcinema.model.cinema.Seat;

/** Defaults preserve the layout of rooms/seats created before the visual editor existed. */
public final class SeatLayoutSupport {
    private SeatLayoutSupport() {}

    public static int widthOf(Seat seat) {
        return seat.getWidthUnits() != null ? seat.getWidthUnits() : seat.getSeatType().getSeatSpan();
    }

    public static Settings settingsOf(Auditorium room) {
        return new Settings(room.getSeatWidth() == null ? 38 : room.getSeatWidth(),
                room.getSeatHeight() == null ? 36 : room.getSeatHeight(),
                room.getColumnGap() == null ? 7 : room.getColumnGap(),
                room.getRowGap() == null ? 7 : room.getRowGap(),
                room.getScreenPosition() == null ? "TOP" : room.getScreenPosition());
    }

    public static void applySettings(Auditorium room, Settings settings) {
        room.setSeatWidth(settings.seatWidth());
        room.setSeatHeight(settings.seatHeight());
        room.setColumnGap(settings.columnGap());
        room.setRowGap(settings.rowGap());
        room.setScreenPosition(settings.screenPosition());
    }
}
