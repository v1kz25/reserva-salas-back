package com.reservasalas.exception;

/**
 * Se lanza cuando el organizador intenta invitarse a su propia reserva.
 */
public final class SelfInvitationException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al campo {@code usuarioId}.
     */
    public SelfInvitationException() {
        super("usuarioId", "no puede ser tu propio usuario");
    }
}
