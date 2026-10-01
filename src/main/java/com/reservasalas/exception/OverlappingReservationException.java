package com.reservasalas.exception;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Se lanza cuando una reserva se solapa con otra de la misma sala y día.
 */
public final class OverlappingReservationException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con la franja que se intentaba reservar.
     *
     * @param roomId    identificador de la sala
     * @param date      día de la reserva
     * @param startTime hora de inicio solicitada
     * @param endTime   hora de fin solicitada
     */
    public OverlappingReservationException(Long roomId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        super("La sala %d ya está reservada el %s en la franja de %s a %s"
                .formatted(roomId, date, startTime, endTime));
    }
}
