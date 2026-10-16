package com.reservasalas.repository;

import com.reservasalas.dto.FriendDto;
import com.reservasalas.entity.FriendRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de solicitudes de amistad y amistades.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /**
     * Busca la solicitud o amistad que hay entre dos usuarios, en cualquier sentido.
     *
     * @param userLowId  el menor de los dos identificadores
     * @param userHighId el mayor de los dos identificadores
     * @return la solicitud o amistad, si existe
     */
    Optional<FriendRequest> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    /**
     * Busca la solicitud o amistad que hay entre dos usuarios, en cualquier sentido, y la bloquea
     * para escritura hasta el fin de la transacción. Así, si dos peticiones la eliminan a la vez, la
     * segunda espera y después ya no la encuentra.
     *
     * @param userLowId  el menor de los dos identificadores
     * @param userHighId el mayor de los dos identificadores
     * @return la solicitud o amistad bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f
            from FriendRequest f
            where f.userLowId = :userLowId
              and f.userHighId = :userHighId""")
    Optional<FriendRequest> findBetweenForUpdate(@Param("userLowId") Long userLowId,
                                                 @Param("userHighId") Long userHighId);

    /**
     * Busca una solicitud y la bloquea para escritura hasta el fin de la transacción, para
     * serializar su aceptación, su rechazo y su retirada.
     *
     * @param id identificador de la solicitud
     * @return la solicitud bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FriendRequest f where f.id = :id")
    Optional<FriendRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lista los amigos activos de un usuario, ordenados por la clave de búsqueda de su nombre (sin
     * distinguir mayúsculas ni acentos), luego por nombre y por identificador.
     *
     * @param userId identificador del usuario
     * @return los amigos, vacía si no tiene ninguno
     */
    @Query("""
            select new com.reservasalas.dto.FriendDto(u.id, u.name, f.acceptedAt)
            from FriendRequest f
            join User u on (u = f.requester or u = f.recipient)
            where f.status = com.reservasalas.entity.FriendRequestStatus.ACCEPTED
              and (f.requester.id = :userId or f.recipient.id = :userId)
              and u.id <> :userId
              and u.active = true
            order by u.nameSearch, u.name, u.id""")
    List<FriendDto> findActiveFriends(@Param("userId") Long userId);

    /**
     * Lista las solicitudes pendientes recibidas por un usuario cuyo solicitante está activo, las
     * más recientes primero, con el solicitante ya cargado.
     *
     * @param recipientId identificador del destinatario
     * @return las solicitudes, vacía si no hay ninguna
     */
    @Query("""
            select f
            from FriendRequest f
            join fetch f.requester r
            where f.recipient.id = :recipientId
              and f.status = com.reservasalas.entity.FriendRequestStatus.PENDING
              and r.active = true
            order by f.createdAt desc, f.id desc""")
    List<FriendRequest> findPendingReceived(@Param("recipientId") Long recipientId);

    /**
     * Lista las solicitudes pendientes enviadas por un usuario cuyo destinatario está activo, las
     * más recientes primero, con el destinatario ya cargado.
     *
     * @param requesterId identificador del solicitante
     * @return las solicitudes, vacía si no hay ninguna
     */
    @Query("""
            select f
            from FriendRequest f
            join fetch f.recipient r
            where f.requester.id = :requesterId
              and f.status = com.reservasalas.entity.FriendRequestStatus.PENDING
              and r.active = true
            order by f.createdAt desc, f.id desc""")
    List<FriendRequest> findPendingSent(@Param("requesterId") Long requesterId);
}
