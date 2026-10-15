package com.reservasalas.repository;

import com.reservasalas.entity.Reservation;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros opcionales para la búsqueda de reservas.
 */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    /**
     * Filtra por sala.
     *
     * @param roomId identificador de la sala; si es nulo, no filtra
     * @return especificación aplicable; sin restricción si no hay filtro
     */
    public static Specification<Reservation> hasRoom(Long roomId) {
        if (roomId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("room").get("id"), roomId);
    }

    /**
     * Filtra las reservas de ese día o posteriores.
     *
     * @param from primer día incluido; si es nulo, no filtra
     * @return especificación aplicable; sin restricción si no hay filtro
     */
    public static Specification<Reservation> onOrAfter(LocalDate from) {
        if (from == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    /**
     * Filtra las reservas de ese día o anteriores.
     *
     * @param to último día incluido; si es nulo, no filtra
     * @return especificación aplicable; sin restricción si no hay filtro
     */
    public static Specification<Reservation> onOrBefore(LocalDate to) {
        if (to == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }
}
