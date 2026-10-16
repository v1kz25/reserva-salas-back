package com.reservasalas.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Estado de una invitación a una reserva ({@code EstadoInvitacion} en el contrato). En la base de
 * datos se guarda el nombre de la constante; en la API, el valor en español.
 */
public enum InvitationStatus {

    /** Enviada y a la espera de que el invitado responda. Ocupa plaza en el aforo. */
    @JsonProperty("PENDIENTE")
    PENDING,

    /** Aceptada: el invitado asistirá. Ocupa plaza en el aforo. */
    @JsonProperty("ACEPTADA")
    ACCEPTED,

    /** Rechazada por el invitado. No ocupa plaza y el organizador puede volver a invitarle. */
    @JsonProperty("RECHAZADA")
    REJECTED;

    /**
     * Indica si la invitación ocupa plaza en el aforo y da acceso al detalle completo de la reserva.
     *
     * @return {@code true} si está pendiente o aceptada
     */
    public boolean isActive() {
        return this != REJECTED;
    }
}
