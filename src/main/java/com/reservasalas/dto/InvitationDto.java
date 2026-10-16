package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Invitation;
import com.reservasalas.entity.InvitationStatus;

/**
 * Invitación a una reserva tal y como se expone en la API ({@code Invitacion} en el contrato).
 *
 * @param id            identificador de la invitación
 * @param reservationId identificador de la reserva
 * @param userId        identificador del usuario invitado
 * @param name          nombre del usuario invitado
 * @param status        estado de la invitación
 */
public record InvitationDto(
        @JsonProperty("id") Long id,
        @JsonProperty("reservaId") Long reservationId,
        @JsonProperty("usuarioId") Long userId,
        @JsonProperty("nombre") String name,
        @JsonProperty("estado") InvitationStatus status) {

    /**
     * Construye el DTO a partir de la entidad. No inicializa la reserva, pero sí el usuario invitado
     * para obtener su nombre, así que debe llamarse dentro de la transacción (o con el usuario ya
     * cargado).
     *
     * @param invitation invitación persistida
     * @return DTO equivalente
     */
    public static InvitationDto from(Invitation invitation) {
        return new InvitationDto(
                invitation.getId(),
                invitation.getReservation().getId(),
                invitation.getUser().getId(),
                invitation.getUser().getName(),
                invitation.getStatus());
    }
}
