package com.reservasalas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Room;
import com.reservasalas.exception.InvalidDateRangeException;
import com.reservasalas.exception.InvalidReservationException;
import com.reservasalas.exception.OverlappingReservationException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** Ahora: 2026-10-01 10:00 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RoomRepository roomRepository;

    private ReservationService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, roomRepository, CLOCK);
        room = new Room("Sala A", 8, 1);
    }

    private CreateReservationRequest request(LocalDate date, String start, String end) {
        return new CreateReservationRequest(1L, date, LocalTime.parse(start), LocalTime.parse(end),
                "Ana", "Reunión");
    }

    private void roomExists() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
    }

    @Test
    void creaReservaFutura() {
        roomExists();
        LocalDate date = TODAY.plusDays(1);
        when(reservationRepository.existsOverlapping(any(), eq(date), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = service.create(request(date, "10:00", "11:00"));

        assertThat(dto.date()).isEqualTo(date);
        assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(dto.responsible()).isEqualTo("Ana");
        assertThat(dto.reason()).isEqualTo("Reunión");
    }

    @Test
    void hoyLanzaErrorEnFechaAunqueLaHoraSeaFutura() {
        assertThatThrownBy(() -> service.create(request(TODAY, "18:00", "19:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("fecha"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void fechaAnteriorAHoyLanzaErrorEnFecha() {
        assertThatThrownBy(() -> service.create(request(TODAY.minusDays(1), "10:00", "11:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("fecha"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void horaFinIgualAInicioLanzaError() {
        assertThatThrownBy(() -> service.create(request(TODAY.plusDays(1), "10:00", "10:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("horaFin"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void horaFinAnteriorAInicioLanzaError() {
        assertThatThrownBy(() -> service.create(request(TODAY.plusDays(1), "11:00", "10:00")))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void salaInexistenteLanza404() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(TODAY.plusDays(1), "10:00", "11:00")))
                .isInstanceOf(RoomNotFoundException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void solapamientoLanzaConflictoYNoGuarda() {
        roomExists();
        LocalDate date = TODAY.plusDays(1);
        when(reservationRepository.existsOverlapping(any(), eq(date),
                eq(LocalTime.of(10, 30)), eq(LocalTime.of(11, 30)))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(date, "10:30", "11:30")))
                .isInstanceOf(OverlappingReservationException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void creaReservaParaManana() {
        roomExists();
        LocalDate tomorrow = TODAY.plusDays(1);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(request(tomorrow, "10:00", "11:00")).date()).isEqualTo(tomorrow);
    }

    @Test
    void findConRangoInvertidoLanzaErrorEnFechaHastaSinConsultar() {
        assertThatThrownBy(() -> service.find(null, TODAY.plusDays(5), TODAY.plusDays(4)))
                .isInstanceOfSatisfying(InvalidDateRangeException.class,
                        e -> assertThat(e.getField()).isEqualTo("fechaHasta"));
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void findAceptaMismaFechaEnAmbasPuntasYSoloUnaDeEllas() {
        when(reservationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.find(null, TODAY, TODAY)).isEmpty();
        assertThat(service.find(null, TODAY, null)).isEmpty();
        assertThat(service.find(null, null, TODAY)).isEmpty();
        assertThat(service.find(1L, TODAY, TODAY.plusDays(1))).isEmpty();
        verify(reservationRepository, times(4)).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void findOrdenaPorFechaYHoraInicio() {
        when(reservationRepository.findAll(any(Specification.class), eq(Sort.by("date", "startTime"))))
                .thenReturn(List.of());

        service.find(null, null, null);

        verify(reservationRepository).findAll(any(Specification.class), eq(Sort.by("date", "startTime")));
    }
}
