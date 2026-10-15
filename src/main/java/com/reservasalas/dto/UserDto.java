package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Role;
import com.reservasalas.security.AuthenticatedUser;

/**
 * Usuario tal y como se expone en la API ({@code Usuario} en el contrato).
 *
 * @param id    identificador
 * @param email email normalizado
 * @param name  nombre visible
 * @param role  rol del usuario
 */
public record UserDto(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("nombre") String name,
        @JsonProperty("rol") Role role) {

    /**
     * Construye el DTO a partir del usuario autenticado.
     *
     * @param user usuario de la sesión
     * @return DTO equivalente
     */
    public static UserDto from(AuthenticatedUser user) {
        return new UserDto(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
