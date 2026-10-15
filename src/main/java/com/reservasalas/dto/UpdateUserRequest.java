package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cambio de nombre, rol y estado de un usuario ({@code UsuarioCambioRequest} en el contrato). El
 * nombre llega ya limpio (forma Unicode NFC y sin espacios en los extremos). Los campos
 * desconocidos se ignoran.
 *
 * @param name   nombre visible, de 1 a 100 caracteres
 * @param role   rol del usuario
 * @param active {@code true} si el usuario puede iniciar sesión
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserRequest(
        @JsonProperty("nombre")
        @NotBlank(message = "es obligatorio")
        @Size(max = User.MAX_NAME_LENGTH, message = "no puede superar los 100 caracteres")
        String name,

        @JsonProperty("rol")
        @NotNull(message = "es obligatorio")
        Role role,

        @JsonProperty("activo")
        @NotNull(message = "es obligatorio")
        Boolean active) {

    /**
     * Limpia el nombre para validar el valor que se guardará.
     */
    public UpdateUserRequest {
        name = User.cleanName(name);
    }
}
