package com.reservasalas.repository;

import com.reservasalas.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * Busca una reserva por su identificador, cargando a la vez su usuario.
     *
     * @param id identificador de la reserva
     * @return la reserva, si existe
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Reservation> findById(Long id);

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
     * misma reserva no fallen: la segunda simplemente no borra nada.
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
}
