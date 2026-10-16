package com.reservasalas.exception;

/**
 * Se lanza al enviar una solicitud de amistad a alguien con quien ya hay una amistad o una
 * solicitud pendiente, en cualquier sentido.
 */
public final class FriendshipConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    private FriendshipConflictException(String message) {
        super(message);
    }

    /**
     * Los dos usuarios ya son amigos.
     *
     * @return la excepción
     */
    public static FriendshipConflictException alreadyFriends() {
        return new FriendshipConflictException("Ya sois amigos");
    }

    /**
     * Ya hay una solicitud pendiente entre los dos usuarios.
     *
     * @return la excepción
     */
    public static FriendshipConflictException pendingRequest() {
        return new FriendshipConflictException("Ya hay una solicitud de amistad pendiente entre vosotros");
    }

    /**
     * La base de datos ha rechazado la solicitud porque otra petición simultánea ha creado antes una
     * relación entre los dos usuarios; no se sabe si es una amistad o una solicitud.
     *
     * @return la excepción
     */
    public static FriendshipConflictException alreadyRelated() {
        return new FriendshipConflictException("Ya sois amigos o ya hay una solicitud pendiente entre vosotros");
    }
}
