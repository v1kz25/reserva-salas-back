package com.reservasalas.exception;

/**
 * Se lanza cuando un parámetro de filtro del listado de reservas tiene un valor no admitido.
 */
public final class InvalidReservationFilterException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al parámetro indicado.
     *
     * @param field   nombre del parámetro en la API
     * @param message descripción del problema
     */
    public InvalidReservationFilterException(String field, String message) {
        super(field, message);
    }
}
