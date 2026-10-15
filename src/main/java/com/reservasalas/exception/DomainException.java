package com.reservasalas.exception;

/**
 * Base de las excepciones de negocio de la aplicación.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con un mensaje apto para mostrar al cliente.
     *
     * @param message descripción del error
     */
    protected DomainException(String message) {
        super(message);
    }
}
