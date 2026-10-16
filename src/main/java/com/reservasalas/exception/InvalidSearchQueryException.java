package com.reservasalas.exception;

/**
 * Se lanza cuando el texto del buscador de usuarios, ya recortado, es demasiado corto o demasiado
 * largo.
 */
public final class InvalidSearchQueryException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al parámetro {@code q}.
     *
     * @param minLength longitud mínima admitida
     * @param maxLength longitud máxima admitida
     */
    public InvalidSearchQueryException(int minLength, int maxLength) {
        super("q", "debe tener entre " + minLength + " y " + maxLength + " caracteres");
    }
}
