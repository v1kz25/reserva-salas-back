package com.reservasalas.exception;

/**
 * Se lanza cuando un filtro por rango de fechas es incoherente (el final es anterior al inicio).
 */
public final class InvalidDateRangeException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al parámetro que cierra el rango.
     *
     * @param field   nombre del parámetro en la API
     * @param message descripción del problema
     */
    public InvalidDateRangeException(String field, String message) {
        super(field, message);
    }
}
