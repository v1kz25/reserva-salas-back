package com.reservasalas.exception;

/**
 * Se lanza cuando una reserva incumple una regla de negocio ligada a un campo concreto
 * (por ejemplo, horas invertidas o fecha que no es posterior a hoy).
 */
public final class InvalidReservationException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada a un campo de la petición.
     *
     * @param field   nombre del campo en la API
     * @param message descripción del problema
     */
    public InvalidReservationException(String field, String message) {
        super(field, message);
    }
}
