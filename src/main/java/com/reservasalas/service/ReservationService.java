package com.reservasalas.service;

import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Room;
import com.reservasalas.exception.InvalidDateRangeException;
import com.reservasalas.exception.InvalidReservationException;
import com.reservasalas.exception.OverlappingReservationException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.ReservationSpecifications;
import com.reservasalas.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las reservas: consulta y alta sin solapamientos.
 */
@Service
public class ReservationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationService.class);
    private static final Sort BY_DATE_AND_START_TIME = Sort.by("date", "startTime");

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    /**
     * Crea el servicio.
     *
     * @param reservationRepository repositorio de reservas
     * @param roomRepository        repositorio de salas
     * @param clock                 reloj con el que se decide qué día es hoy
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              Clock clock) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    /**
     * Lista las reservas ordenadas por fecha y hora de inicio, con filtros opcionales.
     *
     * @param roomId identificador de la sala; si es nulo, no filtra por sala
     * @param from   primer día incluido; si es nulo, no hay límite inferior
     * @param to     último día incluido; si es nulo, no hay límite superior
     * @return las reservas que cumplen los filtros, vacía si no hay ninguna
     * @throws InvalidDateRangeException si se indican ambos días y {@code to} es anterior a {@code from}
     */
    @Transactional(readOnly = true)
    public List<ReservationDto> find(Long roomId, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidDateRangeException("fechaHasta", "no puede ser anterior a fechaDesde");
        }
        Specification<Reservation> filter = Specification.allOf(
                ReservationSpecifications.hasRoom(roomId),
                ReservationSpecifications.onOrAfter(from),
                ReservationSpecifications.onOrBefore(to));
        return reservationRepository.findAll(filter, BY_DATE_AND_START_TIME).stream()
                .map(ReservationDto::from)
                .toList();
    }

    /**
     * Crea una reserva comprobando las reglas de negocio. La sala se bloquea durante la
     * transacción para que dos altas simultáneas no puedan solaparse.
     *
     * @param request datos de la reserva
     * @return la reserva creada
     * @throws InvalidReservationException     si la hora de fin no es posterior a la de inicio
     *                                         o la fecha no es posterior a hoy
     * @throws RoomNotFoundException           si la sala no existe
     * @throws OverlappingReservationException si se solapa con otra reserva de la misma sala y día
     */
    @Transactional
    public ReservationDto create(CreateReservationRequest request) {
        validateTimes(request);

        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new RoomNotFoundException(request.roomId()));

        if (reservationRepository.existsOverlapping(room.getId(), request.date(),
                request.startTime(), request.endTime())) {
            throw new OverlappingReservationException(room.getId(), request.date(),
                    request.startTime(), request.endTime());
        }

        Reservation saved = reservationRepository.save(new Reservation(room, request.date(),
                request.startTime(), request.endTime(), request.responsible(), request.reason()));
        LOG.info("Reserva {} creada en la sala {} el {} de {} a {}", saved.getId(), room.getId(),
                saved.getDate(), saved.getStartTime(), saved.getEndTime());

        return ReservationDto.from(saved);
    }

    /**
     * Comprueba que la franja es coherente y que la reserva es a partir de mañana.
     */
    private void validateTimes(CreateReservationRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidReservationException("horaFin", "debe ser posterior a horaInicio");
        }
        if (!request.date().isAfter(LocalDate.now(clock))) {
            throw new InvalidReservationException("fecha", "debe ser posterior a hoy");
        }
    }
}
