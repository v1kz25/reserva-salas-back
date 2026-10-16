package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Datos para enviar una solicitud de amistad ({@code SolicitudAmistadRequest} en el contrato).
 *
 * @param userId identificador del usuario al que se envía
 */
public record SendFriendRequestRequest(
        @JsonProperty("usuarioId") @NotNull(message = "es obligatorio") Long userId) {
}
