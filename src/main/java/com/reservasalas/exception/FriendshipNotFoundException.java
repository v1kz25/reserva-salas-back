package com.reservasalas.exception;

/**
 * Se lanza al eliminar una amistad que no existe: el usuario indicado no es amigo del de la sesión.
 */
public final class FriendshipNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para el usuario indicado.
     *
     * @param userId identificador del otro usuario
     */
    public FriendshipNotFoundException(Long userId) {
        super("No eres amigo del usuario " + userId);
    }
}
