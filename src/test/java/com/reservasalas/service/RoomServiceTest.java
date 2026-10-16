package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reservasalas.dto.RoomRequest;
import com.reservasalas.entity.Room;
import com.reservasalas.exception.DuplicateRoomNameException;
import com.reservasalas.exception.RoomHasFutureReservationsException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class RoomServiceTest {

    private RoomRepository rooms;
    private ReservationRepository reservations;
    private RoomService service;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        reservations = mock(ReservationRepository.class);
        // 2026-10-01 23:30 UTC ya es 2026-10-02 en Madrid.
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T23:30:00Z"), ZoneId.of("Europe/Madrid"));
        service = new RoomService(rooms, reservations, new CurrentTimeZone(clock));
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("x", new ConstraintViolationException(
                "x", new SQLException("x"), constraint));
    }

    @Test
    void createTraduceLaViolacionDeLaRestriccionUnicaA409AunqueLaComprobacionPreviaPase() {
        when(rooms.existsByNameKey("sala a")).thenReturn(false);
        when(rooms.saveAndFlush(any(Room.class))).thenThrow(violation("UK_ROOMS_NAME_KEY"));

        assertThatThrownBy(() -> service.create(new RoomRequest("Sala A", 5, 1)))
                .isInstanceOf(DuplicateRoomNameException.class)
                .hasMessageNotContaining("Sala A");
    }

    @Test
    void createNoTraduceOtrasViolacionesDeIntegridad() {
        when(rooms.existsByNameKey("sala a")).thenReturn(false);
        when(rooms.saveAndFlush(any(Room.class))).thenThrow(violation("OTRA_RESTRICCION"));

        assertThatThrownBy(() -> service.create(new RoomRequest("Sala A", 5, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createConNombreDuplicadoNoGuarda() {
        when(rooms.existsByNameKey("sala a")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RoomRequest("  Sala A ", 5, 1)))
                .isInstanceOf(DuplicateRoomNameException.class);
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void updateTraduceLaViolacionDeLaRestriccionUnicaA409() {
        Room room = new Room("Vieja", 5, 1);
        when(rooms.findByIdForUpdate(3L)).thenReturn(Optional.of(room));
        when(rooms.existsByNameKeyAndIdNot("sala a", 3L)).thenReturn(false);
        org.mockito.Mockito.doThrow(violation("UK_ROOMS_NAME_KEY")).when(rooms).flush();

        assertThatThrownBy(() -> service.update(3L, new RoomRequest("Sala A", 5, 1)))
                .isInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void updateConNombreDuplicadoDeOtraSalaDa409() {
        when(rooms.findByIdForUpdate(3L)).thenReturn(Optional.of(new Room("Vieja", 5, 1)));
        when(rooms.existsByNameKeyAndIdNot("sala a", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, new RoomRequest("SALA A", 5, 1)))
                .isInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void updateDeSalaInexistenteLanza404() {
        when(rooms.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(9L, new RoomRequest("A", 1, 0)))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void deleteDeSalaInexistenteLanza404() {
        when(rooms.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(RoomNotFoundException.class);
        verify(rooms, never()).deleteById(anyLong());
    }

    @Test
    void deleteBloqueaLaSalaYUsaLaFechaDeHoyDelReloj() {
        when(rooms.findByIdForUpdate(4L)).thenReturn(Optional.of(new Room("A", 1, 0)));
        when(reservations.existsByRoomFrom(4L, LocalDate.of(2026, 10, 2))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(4L)).isInstanceOf(RoomHasFutureReservationsException.class);
        verify(reservations, never()).deleteByRoomId(anyLong());
        verify(rooms, never()).deleteById(anyLong());
    }

    @Test
    void deleteSinReservasFuturasBorraReservasYLuegoLaSala() {
        when(rooms.findByIdForUpdate(4L)).thenReturn(Optional.of(new Room("A", 1, 0)));
        when(reservations.existsByRoomFrom(4L, LocalDate.of(2026, 10, 2))).thenReturn(false);
        when(reservations.deleteByRoomId(4L)).thenReturn(2);

        service.delete(4L);

        InOrder order = inOrder(rooms, reservations);
        order.verify(rooms).findByIdForUpdate(4L);
        order.verify(reservations).deleteByRoomId(4L);
        order.verify(rooms).deleteById(4L);
    }
}
