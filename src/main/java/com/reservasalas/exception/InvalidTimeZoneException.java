package com.reservasalas.exception;

/**
 * Se lanza cuando la zona horaria pedida no es un identificador IANA de región válido.
 */
public final class InvalidTimeZoneException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al campo {@code zonaHoraria}.
     */
    public InvalidTimeZoneException() {
        super("zonaHoraria", "no es un identificador IANA de zona horaria válido, p. ej. Europe/Madrid");
    }
}
