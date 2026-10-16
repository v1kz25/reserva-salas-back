package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Reserva tal y como se expone en la API ({@code Reserva} en el contrato).
 *
 * <p>Los campos nulos no se serializan: con {@link ReservationDetail#BUSY} solo se envían el id, la
 * sala, la fecha, la franja y el detalle, y las invitaciones solo se envían en el detalle completo
 * de una reserva concreta.
 *
 * @param id          identificador
 * @param roomId      identificador de la sala reservada
 * @param date        día de la reserva
 * @param startTime   hora de inicio, en formato {@code HH:mm}
 * @param endTime     hora de fin, en formato {@code HH:mm}
 * @param userId      identificador del usuario que hizo la reserva (organizador); nulo si el detalle
 *                    es {@link ReservationDetail#BUSY}
 * @param responsible nombre del organizador; nulo si el detalle es {@link ReservationDetail#BUSY}
 * @param reason      motivo de la reserva; nulo si el detalle es {@link ReservationDetail#BUSY}
 * @param detail      nivel de detalle con el que la ve el usuario de la sesión
 * @param invitations invitaciones de la reserva; nulas si no se envían
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationDto(
        @JsonProperty("id") Long id,
        @JsonProperty("salaId") Long roomId,
        @JsonProperty("fecha") LocalDate date,
        @JsonProperty("horaInicio") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonProperty("horaFin") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonProperty("usuarioId") Long userId,
        @JsonProperty("responsable") String responsible,
        @JsonProperty("motivo") String reason,
        @JsonProperty("detalle") ReservationDetail detail,
        @JsonProperty("invitaciones") List<InvitationDto> invitations) {

    /**
     * Copia la lista de invitaciones, si la hay, para que el DTO sea inmutable.
     *
     * @param id          identificador
     * @param roomId      identificador de la sala
     * @param date        día de la reserva
     * @param startTime   hora de inicio
     * @param endTime     hora de fin
     * @param userId      identificador del organizador
     * @param responsible nombre del organizador
     * @param reason      motivo
     * @param detail      nivel de detalle
     * @param invitations invitaciones, o {@code null} si no se envían
     */
    public ReservationDto {
        if (invitations != null) {
            invitations = List.copyOf(invitations);
        }
    }

    /**
     * Construye el detalle completo de la reserva, sin invitaciones. Solo lee el id de la sala, así
     * que no la inicializa; el usuario sí se inicializa para obtener su nombre, así que debe llamarse
     * dentro de la transacción (o con el usuario ya cargado).
     *
     * @param reservation reserva persistida
     * @return DTO con {@link ReservationDetail#FULL}
     */
    public static ReservationDto full(Reservation reservation) {
        return full(reservation, null);
    }

    /**
     * Construye el detalle completo de la reserva con sus invitaciones. Mismas condiciones de carga
     * que {@link #full(Reservation)}.
     *
     * @param reservation reserva persistida
     * @param invitations invitaciones de la reserva, o {@code null} para no enviarlas
     * @return DTO con {@link ReservationDetail#FULL}
     */
    public static ReservationDto full(Reservation reservation, List<InvitationDto> invitations) {
        return new ReservationDto(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getReason(),
                ReservationDetail.FULL,
                invitations);
    }

    /**
     * Construye la vista de franja ocupada, para quien no participa en la reserva: sin organizador,
     * motivo ni invitaciones. No inicializa la sala ni el usuario.
     *
     * @param reservation reserva persistida
     * @return DTO con {@link ReservationDetail#BUSY}
     */
    public static ReservationDto busy(Reservation reservation) {
        return new ReservationDto(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                null,
                null,
                null,
                ReservationDetail.BUSY,
                null);
    }
}
