package com.reservasalas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Room;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Verifica la regla de solapamiento: inicioA &lt; finB y inicioB &lt; finA, misma sala y fecha. */
@DataJpaTest
class ReservationRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private RoomRepository rooms;

    private Room roomA;
    private Room roomB;

    @BeforeEach
    void setUp() {
        roomA = rooms.save(new Room("Sala A", 8, 1));
        roomB = rooms.save(new Room("Sala B", 10, 2));
        reservations.save(new Reservation(roomA, DAY, LocalTime.of(10, 0), LocalTime.of(11, 0), "Ana", "x"));
    }

    private boolean overlaps(Room room, LocalDate date, String start, String end) {
        return reservations.existsOverlapping(room.getId(), date, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void mismaFranjaSolapa() {
        assertThat(overlaps(roomA, DAY, "10:00", "11:00")).isTrue();
    }

    @Test
    void solapeParcialPorElPrincipioSolapa() {
        assertThat(overlaps(roomA, DAY, "09:30", "10:30")).isTrue();
    }

    @Test
    void solapeParcialPorElFinalSolapa() {
        assertThat(overlaps(roomA, DAY, "10:30", "11:30")).isTrue();
    }

    @Test
    void franjaContenidaSolapa() {
        assertThat(overlaps(roomA, DAY, "10:15", "10:45")).isTrue();
    }

    @Test
    void franjaQueContieneSolapa() {
        assertThat(overlaps(roomA, DAY, "09:00", "12:00")).isTrue();
    }

    @Test
    void contiguaPosteriorNoSolapa() {
        assertThat(overlaps(roomA, DAY, "11:00", "12:00")).isFalse();
    }

    @Test
    void contiguaAnteriorNoSolapa() {
        assertThat(overlaps(roomA, DAY, "09:00", "10:00")).isFalse();
    }

    @Test
    void mismaHoraEnOtraSalaNoSolapa() {
        assertThat(overlaps(roomB, DAY, "10:00", "11:00")).isFalse();
    }

    @Test
    void mismaHoraEnOtraFechaNoSolapa() {
        assertThat(overlaps(roomA, DAY.plusDays(1), "10:00", "11:00")).isFalse();
    }

    @Test
    void filtrosYOrdenPorFechaYHoraInicio() {
        reservations.save(new Reservation(roomB, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0), "Luis", "y"));
        reservations.save(new Reservation(roomA, DAY.minusDays(1), LocalTime.of(18, 0), LocalTime.of(19, 0), "Eva", "z"));
        Sort sort = Sort.by("date", "startTime");

        List<Reservation> all = reservations.findAll(
                Specification.allOf(ReservationSpecifications.hasRoom(null), ReservationSpecifications.onOrAfter(null),
                        ReservationSpecifications.onOrBefore(null)), sort);
        assertThat(all).extracting(Reservation::getResponsible).containsExactly("Eva", "Luis", "Ana");

        List<Reservation> byRoom = reservations.findAll(ReservationSpecifications.hasRoom(roomA.getId()), sort);
        assertThat(byRoom).extracting(Reservation::getResponsible).containsExactly("Eva", "Ana");

        List<Reservation> byDate = reservations.findAll(Specification.allOf(
                ReservationSpecifications.onOrAfter(DAY), ReservationSpecifications.onOrBefore(DAY)), sort);
        assertThat(byDate).extracting(Reservation::getResponsible).containsExactly("Luis", "Ana");

        List<Reservation> both = reservations.findAll(Specification.allOf(
                ReservationSpecifications.hasRoom(roomA.getId()), ReservationSpecifications.onOrAfter(DAY),
                ReservationSpecifications.onOrBefore(DAY)), sort);
        assertThat(both).extracting(Reservation::getResponsible).containsExactly("Ana");
    }

    @Test
    void salasOrdenadasPorNombre() {
        rooms.save(new Room("Aula 0", 5, 0));
        assertThat(rooms.findAllByOrderByNameAsc()).extracting(Room::getName)
                .containsExactly("Aula 0", "Sala A", "Sala B");
    }

    private List<String> responsables(Specification<Reservation> spec) {
        return reservations.findAll(spec, Sort.by("date", "startTime")).stream()
                .map(Reservation::getResponsible).toList();
    }

    private void guardarTresDias() {
        reservations.save(new Reservation(roomA, DAY.minusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0), "Antes", "x"));
        reservations.save(new Reservation(roomA, DAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0), "Despues", "x"));
    }

    @Test
    void onOrAfterIncluyeElLimiteYExcluyeElDiaAnterior() {
        guardarTresDias();
        assertThat(responsables(ReservationSpecifications.onOrAfter(DAY))).containsExactly("Ana", "Despues");
        assertThat(responsables(ReservationSpecifications.onOrAfter(DAY.plusDays(1)))).containsExactly("Despues");
        assertThat(responsables(ReservationSpecifications.onOrAfter(DAY.plusDays(2)))).isEmpty();
    }

    @Test
    void onOrBeforeIncluyeElLimiteYExcluyeElDiaPosterior() {
        guardarTresDias();
        assertThat(responsables(ReservationSpecifications.onOrBefore(DAY))).containsExactly("Antes", "Ana");
        assertThat(responsables(ReservationSpecifications.onOrBefore(DAY.minusDays(1)))).containsExactly("Antes");
        assertThat(responsables(ReservationSpecifications.onOrBefore(DAY.minusDays(2)))).isEmpty();
    }

    @Test
    void rangoDeUnSoloDiaDevuelveSoloEseDia() {
        guardarTresDias();
        assertThat(responsables(Specification.allOf(ReservationSpecifications.onOrAfter(DAY),
                ReservationSpecifications.onOrBefore(DAY)))).containsExactly("Ana");
    }

    @Test
    void rangoCombinadoConSalaFiltraPorAmbos() {
        guardarTresDias();
        reservations.save(new Reservation(roomB, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0), "Luis", "y"));
        assertThat(responsables(Specification.allOf(ReservationSpecifications.hasRoom(roomB.getId()),
                ReservationSpecifications.onOrAfter(DAY.minusDays(1)),
                ReservationSpecifications.onOrBefore(DAY.plusDays(1))))).containsExactly("Luis");
        assertThat(responsables(Specification.allOf(ReservationSpecifications.hasRoom(roomA.getId()),
                ReservationSpecifications.onOrAfter(DAY.minusDays(1)),
                ReservationSpecifications.onOrBefore(DAY.plusDays(1))))).containsExactly("Antes", "Ana", "Despues");
    }
}
