package com.reservasalas.repository;

import com.reservasalas.entity.Reservation;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de reservas.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    /**
     * Busca las reservas que cumplen el filtro, cargando a la vez su usuario para poder mostrar el
     * responsable sin una consulta por reserva.
     *
     * @param spec filtro de búsqueda
     * @param sort orden del resultado
     * @return las reservas encontradas, vacía si no hay ninguna
     */
    @Override
    @EntityGraph(attributePaths = "user")
    List<Reservation> findAll(Specification<Reservation> spec, Sort sort);

    /**
     * Busca una página de las reservas que cumplen el filtro, cargando a la vez su usuario para
     * poder mostrar el responsable sin una consulta por reserva.
     *
     * @param spec     filtro de búsqueda
     * @param pageable página, tamaño y orden solicitados
     * @return la página de reservas, con su contenido vacío si está fuera de rango
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    /**
     * Busca una reserva por su identificador, cargando a la vez su usuario.
     *
     * @param id identificador de la reserva
     * @return la reserva, si existe
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Reservation> findById(Long id);

    /**
     * Busca una reserva y la bloquea para escritura hasta el fin de la transacción. Sirve para
     * serializar las operaciones sobre sus invitaciones, de modo que el aforo se respete aunque
     * lleguen invitaciones o respuestas simultáneas.
     *
     * @param id identificador de la reserva
     * @return la reserva bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Comprueba si hay alguna reserva de la sala ese día que se solape con la franja indicada.
     * Dos franjas solapan si {@code inicioA < finB} y {@code inicioB < finA}; las contiguas no solapan.
     *
     * @param roomId    identificador de la sala
     * @param date      día de la reserva
     * @param startTime hora de inicio de la franja
     * @param endTime   hora de fin de la franja
     * @return {@code true} si existe al menos una reserva solapada
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.room.id = :roomId
              AND r.date = :date
              AND r.startTime < :endTime
              AND :startTime < r.endTime
            """)
    boolean existsOverlapping(@Param("roomId") Long roomId,
                              @Param("date") LocalDate date,
                              @Param("startTime") LocalTime startTime,
                              @Param("endTime") LocalTime endTime);

    /**
     * Borra la reserva con una única sentencia, de forma que dos cancelaciones simultáneas de la
     * misma reserva no fallen: la segunda simplemente no borra nada. Sus invitaciones las borra a la
     * vez la base de datos ({@code ON DELETE CASCADE}).
     *
     * <p>Ojo: {@code clearAutomatically = true} vacía todo el contexto de persistencia. Si se llama
     * dentro de una transacción que ya tiene otras entidades cargadas, esas entidades quedan
     * desvinculadas; conviene llamarlo al final de la transacción o volver a cargarlas después.
     *
     * @param id identificador de la reserva
     * @return número de reservas borradas: {@code 1} si existía, {@code 0} si no
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Reservation r WHERE r.id = :id")
    int deleteByIdReturningCount(@Param("id") Long id);

    /**
     * Comprueba si la sala tiene alguna reserva el día indicado o posterior.
     *
     * @param roomId identificador de la sala
     * @param from   primer día que cuenta
     * @return {@code true} si existe al menos una reserva desde ese día
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.room.id = :roomId
              AND r.date >= :from
            """)
    boolean existsByRoomFrom(@Param("roomId") Long roomId, @Param("from") LocalDate from);

    /**
     * Borra todas las reservas de una sala con una única sentencia.
     *
     * <p>Las invitaciones de estas reservas las borra a la vez la base de datos, porque su clave
     * ajena hacia la reserva lleva {@code ON DELETE CASCADE}.
     *
     * <p>Ojo: {@code clearAutomatically = true} vacía todo el contexto de persistencia; las entidades
     * cargadas antes quedan desvinculadas.
     *
     * @param roomId identificador de la sala
     * @return número de reservas borradas
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Reservation r WHERE r.room.id = :roomId")
    int deleteByRoomId(@Param("roomId") Long roomId);
}
