package com.reservasalas.exception;

/**
 * Se lanza cuando el usuario indicado no existe.
 */
public final class UserNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para el usuario indicado.
     *
     * @param userId identificador del usuario que no existe
     */
    public UserNotFoundException(Long userId) {
        super("No existe el usuario " + userId);
    }
}
