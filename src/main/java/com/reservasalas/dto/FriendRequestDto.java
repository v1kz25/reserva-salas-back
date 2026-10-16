package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Solicitud de amistad pendiente ({@code SolicitudAmistad} en el contrato).
 *
 * @param id        identificador de la solicitud
 * @param otherUser la otra persona: quien la envía en las recibidas y a quien va en las enviadas
 * @param sentAt    instante de envío
 */
public record FriendRequestDto(
        @JsonProperty("id") Long id,
        @JsonProperty("usuario") UserSummaryDto otherUser,
        @JsonProperty("fecha") Instant sentAt) {
}
