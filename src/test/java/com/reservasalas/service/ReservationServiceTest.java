package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
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
import com.reservasalas.dto.ReservationFilter;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.exception.ForbiddenOperationException;
import com.reservasalas.exception.InvalidDateRangeException;
import com.reservasalas.exception.InvalidReservationException;
import com.reservasalas.exception.OverlappingReservationException;
import com.reservasalas.exception.ReservationNotFoundException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.InvitationRepository;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestUsers;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock
    private UserRepository userRepository;
    @Mock
    private InvitationRepository invitationRepository;

    private ReservationService service;
    private Room room;
    private User ana;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, roomRepository, userRepository, invitationRepository,
                new CurrentTimeZone(CLOCK));
        room = new Room("Sala A", 8, 1);
        ana = new User("ana@example.com", "Ana", "hash", Role.USER, Instant.EPOCH);
        ReflectionTestUtils.setField(ana, "id", TestUsers.ANA.getId());
    }

    private CreateReservationRequest request(LocalDate date, String start, String end) {
        return new CreateReservationRequest(1L, date, LocalTime.parse(start), LocalTime.parse(end), "Reunión");
    }

    private void roomExists() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
    }

    private void userExists() {
        when(userRepository.getReferenceById(TestUsers.ANA.getId())).thenReturn(ana);
    }

    private ReservationDto create(CreateReservationRequest request) {
        return service.create(request, TestUsers.ANA);
    }

    private Reservation reservationOf(User user) {
        return new Reservation(room, TODAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), user, "Reunión");
    }

    @Test
    void creaReservaFutura() {
        roomExists();
        userExists();
        LocalDate date = TODAY.plusDays(1);
        when(reservationRepository.existsOverlapping(any(), eq(date), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationDto dto = create(request(date, "10:00", "11:00"));

        assertThat(dto.date()).isEqualTo(date);
        assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(dto.userId()).isEqualTo(TestUsers.ANA.getId());
        assertThat(dto.responsible()).isEqualTo("Ana");
        assertThat(dto.reason()).isEqualTo("Reunión");
    }

    @Test
    void hoyLanzaErrorEnFechaAunqueLaHoraSeaFutura() {
        assertThatThrownBy(() -> create(request(TODAY, "18:00", "19:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("fecha"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void fechaAnteriorAHoyLanzaErrorEnFecha() {
        assertThatThrownBy(() -> create(request(TODAY.minusDays(1), "10:00", "11:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("fecha"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void horaFinIgualAInicioLanzaError() {
        assertThatThrownBy(() -> create(request(TODAY.plusDays(1), "10:00", "10:00")))
                .isInstanceOfSatisfying(InvalidReservationException.class,
                        e -> assertThat(e.getField()).isEqualTo("horaFin"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void horaFinAnteriorAInicioLanzaError() {
        assertThatThrownBy(() -> create(request(TODAY.plusDays(1), "11:00", "10:00")))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void salaInexistenteLanza404() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> create(request(TODAY.plusDays(1), "10:00", "11:00")))
                .isInstanceOf(RoomNotFoundException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void solapamientoLanzaConflictoYNoGuarda() {
        roomExists();
        LocalDate date = TODAY.plusDays(1);
        when(reservationRepository.existsOverlapping(any(), eq(date),
                eq(LocalTime.of(10, 30)), eq(LocalTime.of(11, 30)))).thenReturn(true);

        assertThatThrownBy(() -> create(request(date, "10:30", "11:30")))
                .isInstanceOf(OverlappingReservationException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void creaReservaParaManana() {
        roomExists();
        userExists();
        LocalDate tomorrow = TODAY.plusDays(1);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(create(request(tomorrow, "10:00", "11:00")).date()).isEqualTo(tomorrow);
    }

    @Test
    void findConRangoInvertidoLanzaErrorEnFechaHastaSinConsultar() {
        assertThatThrownBy(() -> service.find(new ReservationFilter(null, TODAY.plusDays(5), TODAY.plusDays(4), false, null), 0, 20, TestUsers.ANA))
                .isInstanceOfSatisfying(InvalidDateRangeException.class,
                        e -> assertThat(e.getField()).isEqualTo("fechaHasta"));
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void findAceptaMismaFechaEnAmbasPuntasYSoloUnaDeEllas() {
        when(reservationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.find(new ReservationFilter(null, TODAY, TODAY, false, null), 0, 20, TestUsers.ANA).content()).isEmpty();
        assertThat(service.find(new ReservationFilter(null, TODAY, null, false, null), 0, 20, TestUsers.ANA).content()).isEmpty();
        assertThat(service.find(new ReservationFilter(null, null, TODAY, false, null), 0, 20, TestUsers.ANA).content()).isEmpty();
        assertThat(service.find(new ReservationFilter(1L, TODAY, TODAY.plusDays(1), false, null), 0, 20, TestUsers.ANA).content()).isEmpty();
        verify(reservationRepository, times(4)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findOrdenaPorFechaYHoraInicioConDesempatePorId() {
        Pageable expected = PageRequest.of(0, 20, Sort.by("date", "startTime", "id"));
        when(reservationRepository.findAll(any(Specification.class), eq(expected))).thenReturn(Page.empty(expected));

        service.find(new ReservationFilter(null, null, null, false, null), 0, 20, TestUsers.ANA);

        verify(reservationRepository).findAll(any(Specification.class), eq(expected));
    }

    @Test
    void findByIdDevuelveElDtoDeLaReserva() {
        Reservation reservation = reservationOf(ana);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        ReservationDto dto = service.findById(5L, TestUsers.ANA);

        assertThat(dto.roomId()).isEqualTo(1L);
        assertThat(dto.date()).isEqualTo(TODAY.plusDays(1));
        assertThat(dto.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(dto.responsible()).isEqualTo("Ana");
        assertThat(dto.reason()).isEqualTo("Reunión");
    }

    @Test
    void findByIdInexistenteLanzaReservationNotFound() {
        when(reservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L, TestUsers.ANA))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void cancelBorraLaReservaPropia() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservationOf(ana)));
        when(reservationRepository.deleteByIdReturningCount(5L)).thenReturn(1);

        service.cancel(5L, TestUsers.ANA);

        verify(reservationRepository).deleteByIdReturningCount(5L);
    }

    @Test
    void cancelDeReservaAjenaSinSerAdminLanzaForbiddenYNoBorra() {
        User otro = new User("otro@example.com", "Otro", "hash", Role.USER, Instant.EPOCH);
        ReflectionTestUtils.setField(otro, "id", 99L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservationOf(otro)));

        assertThatThrownBy(() -> service.cancel(5L, TestUsers.ANA))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(reservationRepository, never()).deleteByIdReturningCount(any());
    }

    @Test
    void cancelDeReservaAjenaSiendoAdminLaBorra() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservationOf(ana)));
        when(reservationRepository.deleteByIdReturningCount(5L)).thenReturn(1);

        service.cancel(5L, TestUsers.ADMIN);

        verify(reservationRepository).deleteByIdReturningCount(5L);
    }

    @Test
    void cancelInexistenteLanzaReservationNotFound() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(5L, TestUsers.ANA))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("5");
    }

    @Test
    void cancelYaCanceladaEntreLecturaYBorradoLanzaReservationNotFound() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservationOf(ana)));
        when(reservationRepository.deleteByIdReturningCount(5L)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(5L, TestUsers.ANA))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("5");
    }
}
