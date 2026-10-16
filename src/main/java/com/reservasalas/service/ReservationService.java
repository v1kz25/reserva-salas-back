package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.InvitationDto;
import com.reservasalas.dto.PageDto;
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
import com.reservasalas.repository.InvitationRepository;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.ReservationSpecifications;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las reservas: consulta, alta sin solapamientos y cancelación. El responsable
 * (organizador) de una reserva es siempre el usuario que la crea.
 *
 * <p>Privacidad: el organizador, sus invitados con la invitación pendiente o aceptada y los
 * administradores ven la reserva completa; el resto solo ve la franja ocupada.
 */
@Service
public class ReservationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationService.class);
    private static final Sort BY_DATE_AND_START_TIME = Sort.by("date", "startTime", "id");

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final CurrentTimeZone currentTimeZone;

    /**
     * Crea el servicio.
     *
     * @param reservationRepository repositorio de reservas
     * @param roomRepository        repositorio de salas
     * @param userRepository        repositorio de usuarios
     * @param invitationRepository  repositorio de invitaciones, para decidir el nivel de detalle
     * @param currentTimeZone       zona horaria vigente, con la que se decide qué día es hoy
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository,
                              InvitationRepository invitationRepository,
                              CurrentTimeZone currentTimeZone) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.currentTimeZone = currentTimeZone;
    }

    /**
     * Lista una página de las reservas ordenadas por fecha y hora de inicio (y por id, para que el
     * orden sea estable entre páginas), con filtros opcionales. Cada reserva sale completa u ocupada
     * según participe o no el usuario de la sesión; para decidirlo se consultan en lote las
     * invitaciones del usuario a las reservas de la página.
     *
     * @param roomId      identificador de la sala; si es nulo, no filtra por sala
     * @param from        primer día incluido; si es nulo, no hay límite inferior
     * @param to          último día incluido; si es nulo, no hay límite superior
     * @param mine        si es {@code true}, solo las reservas que organiza el usuario y aquellas en
     *                    las que tiene una invitación pendiente o aceptada, todas completas
     * @param page        número de página, empezando en 0
     * @param size        tamaño de página
     * @param currentUser usuario de la sesión
     * @return la página de reservas que cumplen los filtros; si la página está fuera de rango, su
     *         contenido está vacío pero los totales son correctos. Si el desplazamiento no cabe en un
     *         {@code int} (página enorme), solo se cuentan las reservas, sin consultar el contenido
     * @throws InvalidDateRangeException si se indican ambos días y {@code to} es anterior a {@code from}
     */
    @Transactional(readOnly = true)
    public PageDto<ReservationDto> find(Long roomId, LocalDate from, LocalDate to, boolean mine, int page,
                                        int size, AuthenticatedUser currentUser) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidDateRangeException("fechaHasta", "no puede ser anterior a fechaDesde");
        }
        Specification<Reservation> filter = Specification.allOf(
                ReservationSpecifications.hasRoom(roomId),
                ReservationSpecifications.onOrAfter(from),
                ReservationSpecifications.onOrBefore(to),
                mine ? ReservationSpecifications.organizedByOrInvited(currentUser.getId())
                        : Specification.unrestricted());
        var pageable = PageRequest.of(page, size, BY_DATE_AND_START_TIME);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return PageDto.from(new PageImpl<Reservation>(List.of(), pageable, reservationRepository.count(filter)),
                    ReservationDto::full);
        }
        Page<Reservation> result = reservationRepository.findAll(filter, pageable);
        if (mine) {
            // El filtro organizedByOrInvited ya deja solo las reservas que organiza el usuario o en las
            // que tiene una invitación pendiente o aceptada, así que todas se ven completas y no hace
            // falta consultar sus invitaciones en lote.
            return PageDto.from(result, ReservationDto::full);
        }
        return PageDto.from(result, viewFor(result.getContent(), currentUser));
    }

    /**
     * Obtiene una reserva por su identificador. Si el usuario participa en ella (o es administrador),
     * la devuelve completa y con todas sus invitaciones, sea cual sea su estado; si no, solo la
     * franja ocupada.
     *
     * @param id          identificador de la reserva
     * @param currentUser usuario de la sesión
     * @return la reserva
     * @throws ReservationNotFoundException si la reserva no existe
     */
    @Transactional(readOnly = true)
    public ReservationDto findById(Long id, AuthenticatedUser currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        if (!canSeeFull(reservation, currentUser)) {
            return ReservationDto.busy(reservation);
        }
        List<InvitationDto> invitations = invitationRepository.findByReservationWithUser(id).stream()
                .map(InvitationDto::from)
                .toList();
        return ReservationDto.full(reservation, invitations);
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

        return ReservationDto.full(saved);
    }

    /**
     * Cancela una reserva borrándola, de modo que su franja queda libre para otras reservas. Sus
     * invitaciones las borra a la vez la base de datos ({@code ON DELETE CASCADE}). Un usuario solo
     * puede cancelar sus propias reservas; un administrador, cualquiera.
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
        if (!currentUser.isAdmin() && !reservation.isOrganizedBy(currentUser.getId())) {
            LOG.warn("El usuario {} ha intentado cancelar la reserva {} de otro usuario", currentUser.getId(), id);
            throw new ForbiddenOperationException("La reserva " + id + " es de otro usuario");
        }
        if (reservationRepository.deleteByIdReturningCount(id) == 0) {
            throw new ReservationNotFoundException(id);
        }
        LOG.info("Reserva {} cancelada por el usuario {}", id, currentUser.getId());
    }

    /**
     * Decide si el usuario ve completa una reserva concreta: es administrador, la organiza o tiene
     * una invitación pendiente o aceptada.
     */
    private boolean canSeeFull(Reservation reservation, AuthenticatedUser currentUser) {
        return currentUser.isAdmin()
                || reservation.isOrganizedBy(currentUser.getId())
                || invitationRepository.existsActive(reservation.getId(), currentUser.getId());
    }

    /**
     * Devuelve la transformación que aplica la privacidad a una página de reservas. Las invitaciones
     * del usuario se consultan una sola vez para todas las reservas de la página que no organiza,
     * para no lanzar una consulta por reserva.
     */
    private Function<Reservation, ReservationDto> viewFor(List<Reservation> reservations,
                                                         AuthenticatedUser currentUser) {
        if (currentUser.isAdmin()) {
            return ReservationDto::full;
        }
        Long userId = currentUser.getId();
        List<Long> othersIds = reservations.stream()
                .filter(reservation -> !reservation.isOrganizedBy(userId))
                .map(Reservation::getId)
                .toList();
        Set<Long> invitedIds = othersIds.isEmpty()
                ? Set.of()
                : invitationRepository.findActiveReservationIds(userId, othersIds);
        return reservation -> {
            if (reservation.isOrganizedBy(userId) || invitedIds.contains(reservation.getId())) {
                return ReservationDto.full(reservation);
            }
            return ReservationDto.busy(reservation);
        };
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
