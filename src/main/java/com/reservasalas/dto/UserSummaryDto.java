package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.User;

/**
 * Datos públicos de un usuario ({@code UsuarioResumen} en el contrato): nunca incluye el email.
 *
 * @param id   identificador
 * @param name nombre visible
 */
public record UserSummaryDto(
        @JsonProperty("id") Long id,
        @JsonProperty("nombre") String name) {

    /**
     * Construye el DTO a partir de la entidad.
     *
     * @param user usuario persistido
     * @return DTO equivalente
     */
    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(user.getId(), user.getName());
    }
}
