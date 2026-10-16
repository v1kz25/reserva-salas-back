package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Nivel de detalle con el que el usuario de la sesión ve una reserva ({@code detalle} en el
 * contrato).
 */
public enum ReservationDetail {

    /** Reserva completa: el usuario es el organizador, un invitado pendiente o aceptado, o un administrador. */
    @JsonProperty("COMPLETO")
    FULL,

    /** Solo sala, fecha y franja: el usuario no participa en la reserva. */
    @JsonProperty("OCUPADA")
    BUSY
}
