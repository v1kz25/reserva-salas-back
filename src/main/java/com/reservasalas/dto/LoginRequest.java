package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de inicio de sesión ({@code LoginRequest} en el contrato).
 *
 * @param email    email del usuario; no distingue mayúsculas
 * @param password contraseña en claro, de 1 a 128 caracteres
 */
public record LoginRequest(
        @JsonProperty("email")
        @NotBlank(message = "es obligatorio")
        @Email(message = "no es un email válido")
        @Size(max = 254, message = "no puede superar los 254 caracteres")
        String email,

        @JsonProperty("password")
        @NotEmpty(message = "es obligatoria")
        @Size(max = 128, message = "no puede superar los 128 caracteres")
        String password) {

    /**
     * Representación sin la contraseña, para que nunca acabe en un log.
     *
     * @return el texto sin datos sensibles
     */
    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }
}
