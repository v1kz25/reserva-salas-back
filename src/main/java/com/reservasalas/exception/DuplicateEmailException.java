package com.reservasalas.exception;

/**
 * Se lanza cuando se intenta crear un usuario con un email que ya usa otro, sin distinguir
 * mayúsculas.
 */
public final class DuplicateEmailException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción. El mensaje no repite el email recibido.
     */
    public DuplicateEmailException() {
        super("Ya existe un usuario con ese email");
    }
}
