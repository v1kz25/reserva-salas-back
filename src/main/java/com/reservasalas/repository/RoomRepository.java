package com.reservasalas.repository;

import com.reservasalas.entity.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de salas.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Lista todas las salas ordenadas por nombre.
     *
     * @return las salas, vacía si no hay ninguna
     */
    List<Room> findAllByOrderByNameAsc();

    /**
     * Busca una sala y la bloquea para escritura hasta el fin de la transacción. Sirve para
     * serializar las altas de reservas concurrentes sobre la misma sala y evitar solapamientos.
     *
     * @param id identificador de la sala
     * @return la sala bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
