package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Reserva tal y como se expone en la API ({@code Reserva} en el contrato).
 *
 * @param id          identificador
 * @param roomId      identificador de la sala reservada
 * @param date        día de la reserva
 * @param startTime   hora de inicio, en formato {@code HH:mm}
 * @param endTime     hora de fin, en formato {@code HH:mm}
 * @param responsible persona responsable
 * @param reason      motivo de la reserva
 */
public record ReservationDto(
        @JsonProperty("id") Long id,
        @JsonProperty("salaId") Long roomId,
        @JsonProperty("fecha") LocalDate date,
        @JsonProperty("horaInicio") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonProperty("horaFin") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonProperty("responsable") String responsible,
        @JsonProperty("motivo") String reason) {

    /**
     * Construye el DTO a partir de la entidad. Solo lee el id de la sala, así que no la inicializa.
     *
     * @param reservation reserva persistida
     * @return DTO equivalente
     */
    public static ReservationDto from(Reservation reservation) {
        return new ReservationDto(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getResponsible(),
                reservation.getReason());
    }
}
