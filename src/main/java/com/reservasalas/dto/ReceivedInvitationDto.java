package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Invitación pendiente recibida por el usuario de la sesión ({@code InvitacionRecibida} en el
 * contrato).
 *
 * @param id          identificador de la invitación
 * @param reservation la reserva a la que se invita, con el detalle completo y sin invitaciones
 */
public record ReceivedInvitationDto(
        @JsonProperty("id") Long id,
        @JsonProperty("reserva") ReservationDto reservation) {
}
