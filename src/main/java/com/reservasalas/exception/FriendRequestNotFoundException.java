package com.reservasalas.exception;

/**
 * Se lanza cuando la solicitud de amistad no existe, ya no está pendiente o no le corresponde al
 * usuario de la sesión. No se distingue el motivo para no revelar solicitudes ajenas.
 */
public final class FriendRequestNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para la solicitud indicada.
     *
     * @param requestId identificador de la solicitud
     */
    public FriendRequestNotFoundException(Long requestId) {
        super("No existe la solicitud de amistad pendiente " + requestId);
    }
}
