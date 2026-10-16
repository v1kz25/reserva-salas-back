package com.reservasalas.exception;

/**
 * Se lanza cuando un cambio dejaría la aplicación sin ningún administrador activo.
 */
public final class LastActiveAdminException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con el mensaje para el cliente.
     */
    public LastActiveAdminException() {
        super("No puede quedar la aplicación sin ningún administrador activo");
    }
}
