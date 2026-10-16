package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Zona horaria de la aplicación ({@code ZonaHoraria} en el contrato), tanto en la consulta como
 * en el cambio. Los campos desconocidos se ignoran.
 *
 * @param timeZone identificador IANA de región, p. ej. {@code Europe/Madrid}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TimeZoneDto(
        @JsonProperty("zonaHoraria")
        @NotBlank(message = "es obligatoria")
        String timeZone) {
}
