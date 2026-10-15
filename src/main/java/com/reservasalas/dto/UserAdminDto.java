package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import java.time.Instant;

/**
 * Usuario con los datos que ve un administrador ({@code UsuarioAdmin} en el contrato). Nunca
 * incluye la contraseña ni su hash.
 *
 * @param id        identificador
 * @param email     email normalizado
 * @param name      nombre visible
 * @param role      rol del usuario
 * @param active    {@code true} si puede iniciar sesión
 * @param createdAt instante de creación
 */
public record UserAdminDto(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("nombre") String name,
        @JsonProperty("rol") Role role,
        @JsonProperty("activo") boolean active,
        @JsonProperty("fechaCreacion") Instant createdAt) {

    /**
     * Construye el DTO a partir de la entidad.
     *
     * @param user usuario persistido
     * @return DTO equivalente
     */
    public static UserAdminDto from(User user) {
        return new UserAdminDto(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.isActive(),
                user.getCreatedAt());
    }
}
