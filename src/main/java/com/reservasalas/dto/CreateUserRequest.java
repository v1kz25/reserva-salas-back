package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de alta de un usuario ({@code UsuarioNuevoRequest} en el contrato). El email y el nombre
 * llegan ya limpios (email normalizado; nombre en forma Unicode NFC y sin espacios en los
 * extremos), de modo que las validaciones se aplican a lo que se guardará. Si no se indica el rol,
 * el usuario es {@link Role#USER}. Los campos desconocidos se ignoran.
 *
 * @param email    email con el que iniciará sesión; único sin distinguir mayúsculas
 * @param name     nombre visible, de 1 a 100 caracteres
 * @param password contraseña en claro, de 8 a 72 caracteres (el límite de 72 bytes lo comprueba
 *                 el servicio)
 * @param role     rol del usuario
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(
        @JsonProperty("email")
        @NotBlank(message = "es obligatorio")
        @Email(message = "no es un email válido")
        @Size(max = User.MAX_EMAIL_LENGTH, message = "no puede superar los 254 caracteres")
        String email,

        @JsonProperty("nombre")
        @NotBlank(message = "es obligatorio")
        @Size(max = User.MAX_NAME_LENGTH, message = "no puede superar los 100 caracteres")
        String name,

        @JsonProperty("password")
        @NotNull(message = "es obligatoria")
        @Size(min = PasswordRequest.MIN_LENGTH, max = PasswordRequest.MAX_LENGTH,
                message = "debe tener entre 8 y 72 caracteres")
        String password,

        @JsonProperty("rol")
        Role role) {

    /**
     * Normaliza el email, limpia el nombre y aplica el rol por defecto.
     */
    public CreateUserRequest {
        email = User.normalizeEmail(email);
        name = User.cleanName(name);
        if (role == null) {
            role = Role.USER;
        }
    }

    /**
     * Representación sin la contraseña ni el email, para que nunca acaben en un log.
     *
     * @return el texto sin datos sensibles
     */
    @Override
    public String toString() {
        return "CreateUserRequest[email=***, name=***, password=***, role=" + role + "]";
    }
}
