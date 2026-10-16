package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.InvitationDto;
import com.reservasalas.dto.MyInvitationDto;
import com.reservasalas.dto.PageDto;
import com.reservasalas.dto.ReservationFilter;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
     * según participe o no el usuario de la sesión, y con su invitación pendiente o aceptada si la
     * tiene; para decidirlo se consultan en lote las invitaciones del usuario a las reservas de la
     * página.
     *
     * @param filter      filtros opcionales; con {@code mine}, solo «Mis reservas» (todas completas),
     *                    opcionalmente restringidas al papel indicado
     * @param page        número de página, empezando en 0
     * @param size        tamaño de página
     * @param currentUser usuario de la sesión
     * @return la página de reservas que cumplen los filtros; si la página está fuera de rango, su
     *         contenido está vacío pero los totales son correctos. Si el desplazamiento no cabe en un
     *         {@code int} (página enorme), solo se cuentan las reservas, sin consultar el contenido
     * @throws InvalidDateRangeException si se indican ambos días y {@code to} es anterior a {@code from}
     */
    @Transactional(readOnly = true)
    public PageDto<ReservationDto> find(ReservationFilter filter, int page, int size,
                                        AuthenticatedUser currentUser) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new InvalidDateRangeException("fechaHasta", "no puede ser anterior a fechaDesde");
        }
        Specification<Reservation> spec = Specification.allOf(
                ReservationSpecifications.hasRoom(filter.roomId()),
                ReservationSpecifications.onOrAfter(filter.from()),
                ReservationSpecifications.onOrBefore(filter.to()),
                filter.mine() ? ReservationSpecifications.participatedBy(currentUser.getId(), filter.role())
                        : Specification.unrestricted());
        var pageable = PageRequest.of(page, size, BY_DATE_AND_START_TIME);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return PageDto.from(new PageImpl<Reservation>(List.of(), pageable, reservationRepository.count(spec)),
                    ReservationDto::busy);
        }
        Page<Reservation> result = reservationRepository.findAll(spec, pageable);
        // Con «Mis reservas» el filtro ya deja solo reservas en las que participa el usuario, así que
        // viewFor las devuelve todas completas; la consulta en lote sigue haciendo falta para
        // rellenar miInvitacion.
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
        MyInvitationDto myInvitation = invitationRepository.findByReservationIdAndUserId(id, currentUser.getId())
                .filter(invitation -> invitation.getStatus().isActive())
                .map(MyInvitationDto::from)
                .orElse(null);
        if (!currentUser.isAdmin() && !reservation.isOrganizedBy(currentUser.getId()) && myInvitation == null) {
            return ReservationDto.busy(reservation);
        }
        List<InvitationDto> invitations = invitationRepository.findByReservationWithUser(id).stream()
                .map(InvitationDto::from)
                .toList();
        return ReservationDto.full(reservation, myInvitation, invitations);
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

        return ReservationDto.full(saved, null);
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
     * Devuelve la transformación que aplica la privacidad a una página de reservas y rellena
     * {@code miInvitacion}. Las invitaciones activas del usuario se consultan una sola vez para todas
     * las reservas de la página que no organiza (el organizador nunca está invitado a la suya), para
     * no lanzar una consulta por reserva. También se consultan para un administrador, que ve todas
     * completas pero puede estar invitado.
     */
    private Function<Reservation, ReservationDto> viewFor(List<Reservation> reservations,
                                                         AuthenticatedUser currentUser) {
        Long userId = currentUser.getId();
        List<Long> othersIds = reservations.stream()
                .filter(reservation -> !reservation.isOrganizedBy(userId))
                .map(Reservation::getId)
                .toList();
        Map<Long, MyInvitationDto> myInvitations = othersIds.isEmpty()
                ? Map.of()
                : invitationRepository.findActiveByUserAndReservations(userId, othersIds).stream()
                        .collect(Collectors.toMap(invitation -> invitation.getReservation().getId(),
                                MyInvitationDto::from));
        return reservation -> {
            MyInvitationDto myInvitation = myInvitations.get(reservation.getId());
            if (currentUser.isAdmin() || reservation.isOrganizedBy(userId) || myInvitation != null) {
                return ReservationDto.full(reservation, myInvitation);
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
