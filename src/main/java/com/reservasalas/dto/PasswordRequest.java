package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nueva contraseña de un usuario ({@code PasswordRequest} en el contrato). Los campos desconocidos
 * se ignoran.
 *
 * @param password contraseña en claro, de 8 a 72 caracteres (el límite de 72 bytes lo comprueba el
 *                 servicio)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordRequest(
        @JsonProperty("password")
        @NotNull(message = "es obligatoria")
        @Size(min = PasswordRequest.MIN_LENGTH, max = PasswordRequest.MAX_LENGTH,
                message = "debe tener entre 8 y 72 caracteres")
        String password) {

    /** Longitud mínima de una contraseña, en caracteres. */
    public static final int MIN_LENGTH = 8;

    /** Longitud máxima de una contraseña, en caracteres. */
    public static final int MAX_LENGTH = 72;

    /**
     * Representación sin la contraseña, para que nunca acabe en un log.
     *
     * @return el texto sin datos sensibles
     */
    @Override
    public String toString() {
        return "PasswordRequest[password=***]";
    }
}
