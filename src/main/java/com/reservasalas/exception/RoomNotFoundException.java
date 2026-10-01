package com.reservasalas.exception;

/**
 * Se lanza cuando la sala indicada no existe.
 */
public final class RoomNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para la sala indicada.
     *
     * @param roomId identificador de la sala que no existe
     */
    public RoomNotFoundException(Long roomId) {
        super("No existe la sala " + roomId);
    }
}
