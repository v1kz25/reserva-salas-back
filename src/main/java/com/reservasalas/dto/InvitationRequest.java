package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Datos para invitar a un amigo a una reserva ({@code InvitacionRequest} en el contrato).
 *
 * @param userId identificador del usuario al que se invita
 */
public record InvitationRequest(
        @JsonProperty("usuarioId") @NotNull(message = "es obligatorio") Long userId) {
}
