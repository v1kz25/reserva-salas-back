package com.reservasalas.service;

import com.reservasalas.dto.FriendDto;
import com.reservasalas.dto.FriendRequestDto;
import com.reservasalas.dto.UserSummaryDto;
import com.reservasalas.entity.FriendRequest;
import com.reservasalas.entity.User;
import com.reservasalas.exception.FriendRequestNotFoundException;
import com.reservasalas.exception.FriendshipConflictException;
import com.reservasalas.exception.FriendshipNotFoundException;
import com.reservasalas.exception.SelfFriendRequestException;
import com.reservasalas.exception.UniqueConstraintViolations;
import com.reservasalas.exception.UserNotFoundException;
import com.reservasalas.repository.FriendRequestRepository;
import com.reservasalas.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las amistades: envío, retirada, aceptación y rechazo de solicitudes, listado
 * de amigos y eliminación de amistades.
 *
 * <p>Entre dos usuarios hay como mucho una solicitud o amistad, en cualquier sentido; la base de
 * datos lo garantiza con una restricción única sobre el par ordenado de usuarios. Rechazar o retirar
 * una solicitud y eliminar una amistad borran la fila, de modo que después se puede volver a enviar.
 *
 * <p>Los usuarios desactivados no se pueden buscar ni recibir solicitudes. Sus amistades y
 * solicitudes se conservan, pero no aparecen en los listados del otro usuario ni se pueden aceptar
 * mientras sigan desactivados; si se reactivan, vuelven a aparecer.
 */
@Service
public class FriendshipService {

    private static final Logger LOG = LoggerFactory.getLogger(FriendshipService.class);

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Crea el servicio.
     *
     * @param friendRequestRepository repositorio de solicitudes y amistades
     * @param userRepository          repositorio de usuarios
     * @param clock                   reloj con el que se fechan el envío y la aceptación
     */
    public FriendshipService(FriendRequestRepository friendRequestRepository, UserRepository userRepository,
                             Clock clock) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Lista los amigos activos del usuario, ordenados por nombre.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @return los amigos, vacía si no tiene ninguno
     */
    @Transactional(readOnly = true)
    public List<FriendDto> findFriends(Long currentUserId) {
        return friendRequestRepository.findActiveFriends(currentUserId);
    }

    /**
     * Elimina la amistad con otro usuario. Puede hacerlo cualquiera de los dos, también si el otro
     * usuario está desactivado (aunque no aparezca en el listado de amigos), para poder limpiar la
     * relación. La fila se bloquea, de modo que si llegan dos eliminaciones simultáneas la segunda
     * ya no la encuentra.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @param friendId      identificador del amigo
     * @throws FriendshipNotFoundException si no son amigos (tampoco cuando solo hay una solicitud
     *                                     pendiente)
     */
    @Transactional
    public void removeFriend(Long currentUserId, Long friendId) {
        FriendRequest friendship = friendRequestRepository
                .findBetweenForUpdate(Math.min(currentUserId, friendId), Math.max(currentUserId, friendId))
                .filter(Predicate.not(FriendRequest::isPending))
                .orElseThrow(() -> new FriendshipNotFoundException(friendId));
        friendRequestRepository.delete(friendship);
        LOG.info("Amistad entre los usuarios {} y {} eliminada", currentUserId, friendId);
    }

    /**
     * Envía una solicitud de amistad.
     *
     * @param currentUserId identificador del usuario de la sesión, que la envía
     * @param recipientId   identificador del destinatario
     * @return la solicitud creada, con los datos del destinatario
     * @throws SelfFriendRequestException  si el destinatario es el propio usuario
     * @throws UserNotFoundException       si el destinatario no existe o está desactivado
     * @throws FriendshipConflictException si ya son amigos o ya hay una solicitud pendiente en
     *                                     cualquier sentido, también si otra petición simultánea se
     *                                     adelanta
     */
    @Transactional
    public FriendRequestDto sendRequest(Long currentUserId, Long recipientId) {
        if (currentUserId.equals(recipientId)) {
            throw new SelfFriendRequestException();
        }
        User recipient = userRepository.findByIdAndActiveTrue(recipientId)
                .orElseThrow(() -> new UserNotFoundException(recipientId));
        findBetween(currentUserId, recipientId).ifPresent(existing -> {
            throw existing.isPending()
                    ? FriendshipConflictException.pendingRequest()
                    : FriendshipConflictException.alreadyFriends();
        });
        User requester = userRepository.getReferenceById(currentUserId);
        FriendRequest request = new FriendRequest(requester, recipient, now());
        try {
            friendRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e);
        }
        LOG.info("Solicitud de amistad {} enviada por el usuario {} al usuario {}", request.getId(), currentUserId,
                recipientId);
        return new FriendRequestDto(request.getId(), UserSummaryDto.from(recipient), request.getCreatedAt());
    }

    /**
     * Lista las solicitudes pendientes que ha recibido el usuario, las más recientes primero.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @return las solicitudes, con los datos de quien las envía; vacía si no hay ninguna
     */
    @Transactional(readOnly = true)
    public List<FriendRequestDto> findReceived(Long currentUserId) {
        return friendRequestRepository.findPendingReceived(currentUserId).stream()
                .map(request -> toDto(request, request.getRequester()))
                .toList();
    }

    /**
     * Lista las solicitudes pendientes que ha enviado el usuario, las más recientes primero.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @return las solicitudes, con los datos de su destinatario; vacía si no hay ninguna
     */
    @Transactional(readOnly = true)
    public List<FriendRequestDto> findSent(Long currentUserId) {
        return friendRequestRepository.findPendingSent(currentUserId).stream()
                .map(request -> toDto(request, request.getRecipient()))
                .toList();
    }

    /**
     * Retira una solicitud pendiente que ha enviado el usuario. Se permite también si el
     * destinatario está desactivado (aunque no aparezca en la lista de enviadas), para poder limpiar
     * la relación.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @param requestId     identificador de la solicitud
     * @throws FriendRequestNotFoundException si no existe, no la ha enviado él o ya no está pendiente
     */
    @Transactional
    public void withdrawRequest(Long currentUserId, Long requestId) {
        FriendRequest request = findPendingForUpdate(requestId,
                pending -> pending.getRequester().getId().equals(currentUserId));
        friendRequestRepository.delete(request);
        LOG.info("Solicitud de amistad {} retirada por el usuario {}", requestId, currentUserId);
    }

    /**
     * Acepta una solicitud pendiente recibida por el usuario: desde ese momento los dos son amigos.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @param requestId     identificador de la solicitud
     * @throws FriendRequestNotFoundException si no existe, no es para él, ya no está pendiente o
     *                                        quien la envió está desactivado
     */
    @Transactional
    public void acceptRequest(Long currentUserId, Long requestId) {
        FriendRequest request = findPendingForUpdate(requestId,
                pending -> pending.getRecipient().getId().equals(currentUserId)
                        && pending.getRequester().isActive());
        request.accept(now());
        LOG.info("Solicitud de amistad {} aceptada por el usuario {}", requestId, currentUserId);
    }

    /**
     * Rechaza una solicitud pendiente recibida por el usuario. La solicitud se elimina, de modo que
     * se puede volver a enviar más adelante.
     *
     * @param currentUserId identificador del usuario de la sesión
     * @param requestId     identificador de la solicitud
     * @throws FriendRequestNotFoundException si no existe, no es para él o ya no está pendiente
     */
    @Transactional
    public void rejectRequest(Long currentUserId, Long requestId) {
        FriendRequest request = findPendingForUpdate(requestId,
                pending -> pending.getRecipient().getId().equals(currentUserId));
        friendRequestRepository.delete(request);
        LOG.info("Solicitud de amistad {} rechazada por el usuario {}", requestId, currentUserId);
    }

    /**
     * Busca y bloquea una solicitud pendiente que cumpla la condición de acceso indicada. Si no
     * existe, no está pendiente o no la cumple, responde siempre igual para no revelar solicitudes
     * ajenas.
     */
    private FriendRequest findPendingForUpdate(Long requestId, Predicate<FriendRequest> allowed) {
        return friendRequestRepository.findByIdForUpdate(requestId)
                .filter(FriendRequest::isPending)
                .filter(allowed)
                .orElseThrow(() -> new FriendRequestNotFoundException(requestId));
    }

    /**
     * Busca la solicitud o amistad entre dos usuarios, en cualquier sentido, por su par ordenado.
     */
    private Optional<FriendRequest> findBetween(Long userId, Long otherUserId) {
        return friendRequestRepository.findByUserLowIdAndUserHighId(Math.min(userId, otherUserId),
                Math.max(userId, otherUserId));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static FriendRequestDto toDto(FriendRequest request, User otherUser) {
        return new FriendRequestDto(request.getId(), UserSummaryDto.from(otherUser), request.getCreatedAt());
    }

    /**
     * Traduce la violación de la restricción única del par de usuarios a
     * {@link FriendshipConflictException}; cualquier otra violación de integridad se devuelve tal
     * cual para que acabe como error interno.
     */
    private static RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        if (UniqueConstraintViolations.isViolation(e, FriendRequest.UNIQUE_PAIR_CONSTRAINT)) {
            LOG.warn("Solicitud de amistad repetida detectada por la base de datos");
            return FriendshipConflictException.alreadyRelated();
        }
        return e;
    }
}
