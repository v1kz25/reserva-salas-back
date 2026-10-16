package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Amigo del usuario de la sesión ({@code Amigo} en el contrato).
 *
 * @param userId identificador del amigo
 * @param name   nombre visible del amigo
 * @param since  instante en el que se aceptó la amistad
 */
public record FriendDto(
        @JsonProperty("usuarioId") Long userId,
        @JsonProperty("nombre") String name,
        @JsonProperty("desde") Instant since) {
}
