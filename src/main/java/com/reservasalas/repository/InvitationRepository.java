package com.reservasalas.repository;

import com.reservasalas.entity.Invitation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de las invitaciones a reservas.
 */
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * Lista todas las invitaciones de una reserva, sea cual sea su estado, con el invitado ya
     * cargado, ordenadas por el nombre del invitado (sin distinguir mayúsculas ni acentos) y por
     * identificador.
     *
     * @param reservationId identificador de la reserva
     * @return las invitaciones, vacía si no hay ninguna
     */
    @Query("""
            select i
            from Invitation i
            join fetch i.user u
            where i.reservation.id = :reservationId
            order by u.nameSearch, u.name, i.id""")
    List<Invitation> findByReservationWithUser(@Param("reservationId") Long reservationId);

    /**
     * Busca la invitación de un usuario a una reserva, sea cual sea su estado.
     *
     * @param reservationId identificador de la reserva
     * @param userId        identificador del usuario invitado
     * @return la invitación, si existe
     */
    Optional<Invitation> findByReservationIdAndUserId(Long reservationId, Long userId);

    /**
     * Cuenta las invitaciones de una reserva que ocupan plaza en el aforo (pendientes y aceptadas).
     *
     * @param reservationId identificador de la reserva
     * @return número de invitaciones pendientes o aceptadas
     */
    @Query("""
            select count(i)
            from Invitation i
            where i.reservation.id = :reservationId
              and i.status <> com.reservasalas.entity.InvitationStatus.REJECTED""")
    long countActiveByReservation(@Param("reservationId") Long reservationId);

    /**
     * Comprueba si el usuario tiene una invitación pendiente o aceptada a la reserva.
     *
     * @param reservationId identificador de la reserva
     * @param userId        identificador del usuario
     * @return {@code true} si está invitado y no la ha rechazado
     */
    @Query("""
            select count(i) > 0
            from Invitation i
            where i.reservation.id = :reservationId
              and i.user.id = :userId
              and i.status <> com.reservasalas.entity.InvitationStatus.REJECTED""")
    boolean existsActive(@Param("reservationId") Long reservationId, @Param("userId") Long userId);

    /**
     * De entre las reservas indicadas, devuelve aquellas en las que el usuario tiene una invitación
     * pendiente o aceptada. Sirve para decidir en lote el nivel de detalle de una página de reservas.
     *
     * @param userId         identificador del usuario
     * @param reservationIds identificadores de las reservas a comprobar; no debe estar vacía
     * @return los identificadores de las reservas en las que participa como invitado
     */
    @Query("""
            select i.reservation.id
            from Invitation i
            where i.user.id = :userId
              and i.reservation.id in :reservationIds
              and i.status <> com.reservasalas.entity.InvitationStatus.REJECTED""")
    Set<Long> findActiveReservationIds(@Param("userId") Long userId,
                                       @Param("reservationIds") Collection<Long> reservationIds);

    /**
     * Devuelve la reserva de una invitación si el invitado es el usuario indicado, sin cargar la
     * invitación en el contexto de persistencia.
     *
     * @param invitationId identificador de la invitación
     * @param userId       identificador del usuario invitado
     * @return el identificador de la reserva, o vacío si la invitación no existe o no es suya
     */
    @Query("""
            select i.reservation.id
            from Invitation i
            where i.id = :invitationId
              and i.user.id = :userId""")
    Optional<Long> findReservationIdOfInvitee(@Param("invitationId") Long invitationId,
                                              @Param("userId") Long userId);

    /**
     * Lista las invitaciones pendientes de un usuario a reservas que aún no han terminado, con la
     * reserva y su organizador ya cargados, ordenadas por fecha y hora de inicio de la reserva.
     *
     * @param userId identificador del usuario invitado
     * @param today  día de hoy en la zona horaria vigente
     * @param now    hora actual en la zona horaria vigente; las reservas de hoy que ya han terminado
     *               no se incluyen
     * @return las invitaciones, vacía si no hay ninguna
     */
    @Query("""
            select i
            from Invitation i
            join fetch i.reservation r
            join fetch r.user
            where i.user.id = :userId
              and i.status = com.reservasalas.entity.InvitationStatus.PENDING
              and (r.date > :today or (r.date = :today and r.endTime > :now))
            order by r.date, r.startTime, r.id""")
    List<Invitation> findPendingNotEnded(@Param("userId") Long userId,
                                         @Param("today") LocalDate today,
                                         @Param("now") LocalTime now);

    /**
     * Borra una invitación de una reserva con una única sentencia, de forma que dos retiradas
     * simultáneas no fallen: la segunda simplemente no borra nada.
     *
     * @param invitationId  identificador de la invitación
     * @param reservationId identificador de la reserva a la que debe pertenecer
     * @return número de invitaciones borradas: {@code 1} si existía en esa reserva, {@code 0} si no
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Invitation i
            where i.id = :invitationId
              and i.reservation.id = :reservationId""")
    int deleteByIdAndReservationId(@Param("invitationId") Long invitationId,
                                   @Param("reservationId") Long reservationId);
}
