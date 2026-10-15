package com.reservasalas.exception;

/**
 * Se lanza cuando el usuario autenticado no tiene permiso para la operación (por ejemplo, cancelar
 * una reserva de otro usuario sin ser administrador).
 */
public final class ForbiddenOperationException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con una descripción para el log; al cliente se le responde con un mensaje
     * genérico.
     *
     * @param message descripción del motivo
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
