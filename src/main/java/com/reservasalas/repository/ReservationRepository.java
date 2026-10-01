package com.reservasalas.repository;

import com.reservasalas.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de reservas.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

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
}
