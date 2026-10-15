package com.reservasalas.exception;

/**
 * Se lanza cuando se intenta eliminar una sala que tiene reservas de hoy o posteriores.
 */
public final class RoomHasFutureReservationsException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para la sala indicada.
     *
     * @param roomId identificador de la sala
     */
    public RoomHasFutureReservationsException(Long roomId) {
        super("La sala " + roomId + " tiene reservas a partir de hoy y no se puede eliminar");
    }
}
