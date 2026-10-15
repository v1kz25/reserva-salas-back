package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error de validación de un campo ({@code ErrorCampo} en el contrato).
 *
 * @param field   nombre del campo en la API
 * @param message descripción del problema
 */
public record FieldErrorDto(
        @JsonProperty("campo") String field,
        @JsonProperty("mensaje") String message) {
}
