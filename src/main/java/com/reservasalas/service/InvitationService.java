package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.InvitationDto;
import com.reservasalas.dto.ReceivedInvitationDto;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.entity.Invitation;
import com.reservasalas.entity.InvitationStatus;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.User;
import com.reservasalas.exception.ForbiddenOperationException;
import com.reservasalas.exception.InvitationConflictException;
import com.reservasalas.exception.InvitationNotFoundException;
import com.reservasalas.exception.ReservationNotFoundException;
import com.reservasalas.exception.SelfInvitationException;
import com.reservasalas.exception.UniqueConstraintViolations;
import com.reservasalas.repository.FriendRequestRepository;
import com.reservasalas.repository.InvitationRepository;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las invitaciones a reservas: invitar a un amigo, retirar la invitación,
 * listar las pendientes y aceptarlas o rechazarlas.
 *
 * <p>Aforo: el organizador más las invitaciones pendientes y aceptadas no pueden superar la
 * capacidad de la sala. Invitar, aceptar y rechazar bloquean la reserva hasta el fin de la
 * transacción, de modo que las operaciones simultáneas sobre la misma reserva se serializan y el
 * aforo se respeta.
 *
 * <p>Una reserva ha pasado cuando ya ha terminado: su día y hora de fin no son posteriores a la
 * fecha y hora actuales en la zona horaria vigente. Mientras está en curso aún se puede invitar,
 * aceptar y rechazar.
 */
@Service
public class InvitationService {

    private static final Logger LOG = LoggerFactory.getLogger(InvitationService.class);

    private final InvitationRepository invitationRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final CurrentTimeZone currentTimeZone;

    /**
     * Crea el servicio.
     *
     * @param invitationRepository    repositorio de invitaciones
     * @param reservationRepository   repositorio de reservas
     * @param userRepository          repositorio de usuarios
     * @param friendRequestRepository repositorio de amistades, para comprobar que el invitado es amigo
     * @param currentTimeZone         zona horaria vigente, con la que se decide si una reserva ha pasado
     */
    public InvitationService(InvitationRepository invitationRepository,
                             ReservationRepository reservationRepository,
                             UserRepository userRepository,
                             FriendRequestRepository friendRequestRepository,
                             CurrentTimeZone currentTimeZone) {
        this.invitationRepository = invitationRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.currentTimeZone = currentTimeZone;
    }

    /**
     * Invita a un amigo a una reserva del usuario de la sesión. Si el invitado había rechazado una
     * invitación anterior a la misma reserva, esa invitación vuelve a quedar pendiente.
     *
     * @param reservationId identificador de la reserva
     * @param inviteeId     identificador del usuario al que se invita
     * @param currentUser   usuario de la sesión, que debe ser el organizador
     * @return la invitación, pendiente
     * @throws ReservationNotFoundException si la reserva no existe
     * @throws ForbiddenOperationException  si el usuario no es el organizador de la reserva
     * @throws SelfInvitationException      si el organizador se invita a sí mismo
     * @throws InvitationConflictException  si la reserva ya ha pasado, el invitado no es amigo
     *                                      confirmado y activo, ya está invitado (pendiente o
     *                                      aceptado) o se superaría el aforo de la sala
     */
    @Transactional
    public InvitationDto invite(Long reservationId, Long inviteeId, AuthenticatedUser currentUser) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Long organizerId = currentUser.getId();
        if (!reservation.isOrganizedBy(organizerId)) {
            LOG.warn("El usuario {} ha intentado invitar a la reserva {} de otro usuario", organizerId, reservationId);
            throw new ForbiddenOperationException("La reserva " + reservationId + " es de otro usuario");
        }
        if (inviteeId.equals(organizerId)) {
            throw new SelfInvitationException();
        }
        ensureNotEnded(reservation);
        User invitee = userRepository.findByIdAndActiveTrue(inviteeId)
                .filter(user -> areFriends(organizerId, user.getId()))
                .orElseThrow(InvitationConflictException::notFriend);

        var existing = invitationRepository.findByReservationIdAndUserId(reservationId, inviteeId);
        if (existing.map(Invitation::getStatus).filter(InvitationStatus::isActive).isPresent()) {
            throw InvitationConflictException.alreadyInvited();
        }
        ensureCapacityForOneMore(reservation);

        Invitation invitation = existing.orElseGet(() -> new Invitation(reservation, invitee));
        invitation.reinvite();
        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e);
        }
        LOG.info("Usuario {} invitado a la reserva {} con la invitación {}", inviteeId, reservationId,
                invitation.getId());
        return InvitationDto.from(invitation);
    }

    /**
     * Retira una invitación de una reserva del usuario de la sesión, sea cual sea su estado. La
     * reserva se bloquea antes de borrar, igual que al invitar, aceptar y rechazar, para que todas
     * estas operaciones se serialicen con el mismo orden de bloqueo (reserva y después invitación) y
     * una aceptación simultánea no intente actualizar una invitación ya borrada.
     *
     * @param reservationId identificador de la reserva
     * @param invitationId  identificador de la invitación
     * @param currentUser   usuario de la sesión, que debe ser el organizador
     * @throws ReservationNotFoundException si la reserva no existe
     * @throws ForbiddenOperationException  si el usuario no es el organizador de la reserva
     * @throws InvitationNotFoundException  si la invitación no existe o es de otra reserva
     */
    @Transactional
    public void withdraw(Long reservationId, Long invitationId, AuthenticatedUser currentUser) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!reservation.isOrganizedBy(currentUser.getId())) {
            LOG.warn("El usuario {} ha intentado retirar una invitación de la reserva {} de otro usuario",
                    currentUser.getId(), reservationId);
            throw new ForbiddenOperationException("La reserva " + reservationId + " es de otro usuario");
        }
        if (invitationRepository.deleteByIdAndReservationId(invitationId, reservationId) == 0) {
            throw new InvitationNotFoundException(invitationId);
        }
        LOG.info("Invitación {} retirada de la reserva {}", invitationId, reservationId);
    }

    /**
     * Lista las invitaciones pendientes del usuario a reservas que aún no han pasado, ordenadas por
     * fecha y hora de inicio de la reserva.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @return las invitaciones, con la reserva completa y sin invitaciones; vacía si no hay ninguna
     */
    @Transactional(readOnly = true)
    public List<ReceivedInvitationDto> findPending(Long currentUserId) {
        LocalDateTime now = currentTimeZone.now();
        return invitationRepository.findPendingNotEnded(currentUserId, now.toLocalDate(), now.toLocalTime())
                .stream()
                .map(invitation -> new ReceivedInvitationDto(invitation.getId(),
                        ReservationDto.full(invitation.getReservation())))
                .toList();
    }

    /**
     * Acepta una invitación pendiente del usuario de la sesión.
     *
     * @param invitationId  identificador de la invitación
     * @param currentUserId identificador del usuario de la sesión, que debe ser el invitado
     * @throws InvitationNotFoundException si la invitación no existe o no es suya
     * @throws InvitationConflictException si la invitación ya no está pendiente o la reserva ya ha pasado
     */
    @Transactional
    public void accept(Long invitationId, Long currentUserId) {
        Invitation invitation = lockForInvitee(invitationId, currentUserId);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw InvitationConflictException.notPending();
        }
        ensureNotEnded(invitation.getReservation());
        invitation.accept();
        LOG.info("Invitación {} aceptada por el usuario {}", invitationId, currentUserId);
    }

    /**
     * Rechaza una invitación pendiente o aceptada del usuario de la sesión; si estaba aceptada, el
     * usuario deja de asistir. La plaza queda libre en el aforo.
     *
     * @param invitationId  identificador de la invitación
     * @param currentUserId identificador del usuario de la sesión, que debe ser el invitado
     * @throws InvitationNotFoundException si la invitación no existe o no es suya
     * @throws InvitationConflictException si la invitación ya estaba rechazada o la reserva ya ha pasado
     */
    @Transactional
    public void reject(Long invitationId, Long currentUserId) {
        Invitation invitation = lockForInvitee(invitationId, currentUserId);
        if (invitation.getStatus() == InvitationStatus.REJECTED) {
            throw InvitationConflictException.alreadyRejected();
        }
        ensureNotEnded(invitation.getReservation());
        invitation.reject();
        LOG.info("Invitación {} rechazada por el usuario {}", invitationId, currentUserId);
    }

    /**
     * Busca una invitación del usuario y bloquea su reserva antes de leerla, para que su estado sea
     * el vigente y las respuestas simultáneas se serialicen con las invitaciones a la misma reserva.
     * Si la invitación no existe, no es suya o se retira entre medias, responde siempre igual.
     */
    private Invitation lockForInvitee(Long invitationId, Long userId) {
        Long reservationId = invitationRepository.findReservationIdOfInvitee(invitationId, userId)
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));
        reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));
        return invitationRepository.findById(invitationId)
                .filter(invitation -> invitation.getUser().getId().equals(userId))
                .orElseThrow(() -> new InvitationNotFoundException(invitationId));
    }

    private void ensureNotEnded(Reservation reservation) {
        if (reservation.hasEndedBy(currentTimeZone.now())) {
            throw InvitationConflictException.reservationEnded();
        }
    }

    /**
     * Comprueba que cabe una persona más: organizador + pendientes + aceptadas + la nueva invitación
     * no pueden superar la capacidad de la sala. La reserva debe estar bloqueada.
     */
    private void ensureCapacityForOneMore(Reservation reservation) {
        int capacity = reservation.getRoom().getCapacity();
        long attendees = 1 + invitationRepository.countActiveByReservation(reservation.getId());
        if (attendees + 1 > capacity) {
            throw InvitationConflictException.capacityExceeded(capacity);
        }
    }

    /**
     * Indica si los dos usuarios son amigos confirmados (la solicitud entre ellos está aceptada).
     */
    private boolean areFriends(Long userId, Long otherUserId) {
        return friendRequestRepository
                .findByUserLowIdAndUserHighId(Math.min(userId, otherUserId), Math.max(userId, otherUserId))
                .filter(friendship -> !friendship.isPending())
                .isPresent();
    }

    /**
     * Traduce la violación de la restricción única de reserva y usuario a
     * {@link InvitationConflictException}; cualquier otra violación de integridad se devuelve tal
     * cual para que acabe como error interno.
     */
    private static RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        if (UniqueConstraintViolations.isViolation(e, Invitation.UNIQUE_RESERVATION_USER_CONSTRAINT)) {
            LOG.warn("Invitación repetida detectada por la base de datos");
            return InvitationConflictException.alreadyInvited();
        }
        return e;
    }
}
