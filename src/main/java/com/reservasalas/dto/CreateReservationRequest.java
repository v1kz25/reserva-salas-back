package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Datos de alta de una reserva ({@code ReservaRequest} en el contrato). El responsable no viaja en
 * la petición: es siempre el usuario de la sesión. Los campos desconocidos (p. ej. un
 * {@code responsable} enviado por un cliente antiguo) se ignoran.
 *
 * @param roomId      identificador de la sala que se reserva
 * @param date        día de la reserva; debe ser posterior a hoy
 * @param startTime   hora de inicio, en formato {@code HH:mm}
 * @param endTime     hora de fin, en formato {@code HH:mm} y posterior a la de inicio
 * @param reason      motivo de la reserva, de 1 a 255 caracteres
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateReservationRequest(
        @JsonProperty("salaId")
        @NotNull(message = "es obligatorio")
        Long roomId,

        @JsonProperty("fecha")
        @NotNull(message = "es obligatoria")
        LocalDate date,

        @JsonProperty("horaInicio")
        @JsonFormat(pattern = "HH:mm")
        @NotNull(message = "es obligatoria")
        LocalTime startTime,

        @JsonProperty("horaFin")
        @JsonFormat(pattern = "HH:mm")
        @NotNull(message = "es obligatoria")
        LocalTime endTime,

        @JsonProperty("motivo")
        @NotBlank(message = "es obligatorio")
        @Size(max = 255, message = "no puede superar los 255 caracteres")
        String reason) {
}
