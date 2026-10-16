package com.reservasalas.exception;

/**
 * Se lanza cuando un usuario intenta enviarse una solicitud de amistad a sí mismo.
 */
public final class SelfFriendRequestException extends FieldValidationException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción asociada al campo {@code usuarioId}.
     */
    public SelfFriendRequestException() {
        super("usuarioId", "no puede ser tu propio usuario");
    }
}
