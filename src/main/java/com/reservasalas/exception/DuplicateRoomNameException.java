package com.reservasalas.exception;

/**
 * Se lanza cuando se intenta crear o renombrar una sala con un nombre que ya usa otra sala, sin
 * distinguir mayúsculas ni espacios de los extremos.
 */
public final class DuplicateRoomNameException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción. El mensaje no repite el nombre recibido.
     */
    public DuplicateRoomNameException() {
        super("Ya existe otra sala con ese nombre");
    }
}
