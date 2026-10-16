package com.reservasalas.repository;

import com.reservasalas.entity.Invitation;
import com.reservasalas.entity.InvitationStatus;
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

    /**
     * Filtra las reservas en las que participa el usuario: las que organiza y aquellas en las que
     * tiene una invitación pendiente o aceptada.
     *
     * @param userId identificador del usuario
     * @return especificación aplicable
     */
    public static Specification<Reservation> organizedByOrInvited(Long userId) {
        return (root, query, cb) -> {
            var invited = query.subquery(Long.class);
            var invitation = invited.from(Invitation.class);
            invited.select(invitation.get("id"))
                    .where(cb.equal(invitation.get("reservation"), root),
                            cb.equal(invitation.get("user").get("id"), userId),
                            cb.notEqual(invitation.get("status"), InvitationStatus.REJECTED));
            return cb.or(cb.equal(root.get("user").get("id"), userId), cb.exists(invited));
        };
    }
}
