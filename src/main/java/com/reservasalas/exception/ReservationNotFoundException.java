package com.reservasalas.exception;

/**
 * Se lanza cuando la reserva indicada no existe (o ya se ha cancelado).
 */
public final class ReservationNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para la reserva indicada.
     *
     * @param reservationId identificador de la reserva que no existe
     */
    public ReservationNotFoundException(Long reservationId) {
        super("No existe la reserva " + reservationId);
    }
}
