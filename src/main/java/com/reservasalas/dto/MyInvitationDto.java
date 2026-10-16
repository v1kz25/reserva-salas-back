package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Invitation;
import com.reservasalas.entity.InvitationStatus;

/**
 * Invitación del usuario de la sesión a una reserva ({@code MiInvitacion} en el contrato).
 *
 * @param id     identificador de la invitación
 * @param status estado de la invitación: pendiente o aceptada
 */
public record MyInvitationDto(
        @JsonProperty("id") Long id,
        @JsonProperty("estado") InvitationStatus status) {

    /**
     * Construye el DTO a partir de la entidad, sin inicializar sus relaciones.
     *
     * @param invitation invitación persistida
     * @return DTO equivalente
     */
    public static MyInvitationDto from(Invitation invitation) {
        return new MyInvitationDto(invitation.getId(), invitation.getStatus());
    }
}
