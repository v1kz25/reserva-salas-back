package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.exception.ForbiddenOperationException;
import com.reservasalas.exception.InvalidDateRangeException;
import com.reservasalas.exception.InvalidReservationException;
import com.reservasalas.exception.OverlappingReservationException;
import com.reservasalas.exception.ReservationNotFoundException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.ReservationSpecifications;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las reservas: consulta, alta sin solapamientos y cancelación. El responsable
 * de una reserva es siempre el usuario que la crea.
 */
@Service
public class ReservationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationService.class);
    private static final Sort BY_DATE_AND_START_TIME = Sort.by("date", "startTime");

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CurrentTimeZone currentTimeZone;

    /**
     * Crea el servicio.
     *
     * @param reservationRepository repositorio de reservas
     * @param roomRepository        repositorio de salas
     * @param userRepository        repositorio de usuarios
     * @param currentTimeZone       zona horaria vigente, con la que se decide qué día es hoy
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository,
                              CurrentTimeZone currentTimeZone) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.currentTimeZone = currentTimeZone;
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
     * Obtiene una reserva por su identificador.
     *
     * @param id identificador de la reserva
     * @return la reserva
     * @throws ReservationNotFoundException si la reserva no existe
     */
    @Transactional(readOnly = true)
    public ReservationDto findById(Long id) {
        return reservationRepository.findById(id)
                .map(ReservationDto::from)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /**
     * Crea una reserva comprobando las reglas de negocio. La sala se bloquea durante la
     * transacción para que dos altas simultáneas no puedan solaparse.
     *
     * @param request     datos de la reserva
     * @param currentUser usuario de la sesión, que queda como responsable
     * @return la reserva creada
     * @throws InvalidReservationException     si la hora de fin no es posterior a la de inicio
     *                                         o la fecha no es posterior a hoy
     * @throws RoomNotFoundException           si la sala no existe
     * @throws OverlappingReservationException si se solapa con otra reserva de la misma sala y día
     */
    @Transactional
    public ReservationDto create(CreateReservationRequest request, AuthenticatedUser currentUser) {
        validateTimes(request);

        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new RoomNotFoundException(request.roomId()));

        if (reservationRepository.existsOverlapping(room.getId(), request.date(),
                request.startTime(), request.endTime())) {
            throw new OverlappingReservationException(room.getId(), request.date(),
                    request.startTime(), request.endTime());
        }

        User user = userRepository.getReferenceById(currentUser.getId());
        Reservation saved = reservationRepository.save(new Reservation(room, request.date(),
                request.startTime(), request.endTime(), user, request.reason()));
        LOG.info("Reserva {} creada en la sala {} el {} de {} a {}", saved.getId(), room.getId(),
                saved.getDate(), saved.getStartTime(), saved.getEndTime());

        return ReservationDto.from(saved);
    }

    /**
     * Cancela una reserva borrándola, de modo que su franja queda libre para otras reservas. Un
     * usuario solo puede cancelar sus propias reservas; un administrador, cualquiera.
     *
     * @param id          identificador de la reserva
     * @param currentUser usuario de la sesión
     * @throws ReservationNotFoundException si la reserva no existe o ya se había cancelado
     * @throws ForbiddenOperationException  si la reserva es de otro usuario y no es administrador
     */
    @Transactional
    public void cancel(Long id, AuthenticatedUser currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        if (!currentUser.isAdmin() && !reservation.getUser().getId().equals(currentUser.getId())) {
            LOG.warn("El usuario {} ha intentado cancelar la reserva {} de otro usuario", currentUser.getId(), id);
            throw new ForbiddenOperationException("La reserva " + id + " es de otro usuario");
        }
        if (reservationRepository.deleteByIdReturningCount(id) == 0) {
            throw new ReservationNotFoundException(id);
        }
        LOG.info("Reserva {} cancelada por el usuario {}", id, currentUser.getId());
    }

    /**
     * Comprueba que la franja es coherente y que la reserva es a partir de mañana.
     */
    private void validateTimes(CreateReservationRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidReservationException("horaFin", "debe ser posterior a horaInicio");
        }
        if (!request.date().isAfter(currentTimeZone.today())) {
            throw new InvalidReservationException("fecha", "debe ser posterior a hoy");
        }
    }
}
