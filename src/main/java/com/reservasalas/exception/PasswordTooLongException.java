package com.reservasalas.exception;

/**
 * Se lanza cuando la contraseña supera el límite de bytes de BCrypt en UTF-8, aunque no supere el
 * de caracteres (p. ej. por usar caracteres de varios bytes).
 */
public final class PasswordTooLongException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al campo {@code password}.
     *
     * @param maxBytes número máximo de bytes permitido
     */
    public PasswordTooLongException(int maxBytes) {
        super("password", "no puede superar los " + maxBytes + " bytes");
    }
}
