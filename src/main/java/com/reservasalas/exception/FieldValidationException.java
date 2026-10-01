package com.reservasalas.exception;

/**
 * Base de los errores de negocio ligados a un campo concreto de la petición, que se devuelven
 * como {@code 400} con la lista {@code errores}.
 */
public abstract class FieldValidationException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final String field;

    /**
     * Crea la excepción asociada a un campo de la petición.
     *
     * @param field   nombre del campo en la API
     * @param message descripción del problema
     */
    protected FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * Campo de la petición que incumple la regla.
     *
     * @return el nombre del campo en la API
     */
    public String getField() {
        return field;
    }
}
