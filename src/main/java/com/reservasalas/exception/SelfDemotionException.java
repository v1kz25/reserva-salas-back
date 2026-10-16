package com.reservasalas.exception;

/**
 * Se lanza cuando un administrador intenta desactivarse o quitarse el rol de administrador a sí
 * mismo.
 */
public final class SelfDemotionException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con el mensaje para el cliente.
     */
    public SelfDemotionException() {
        super("Un administrador no puede desactivarse ni quitarse el rol de administrador a sí mismo");
    }
}
