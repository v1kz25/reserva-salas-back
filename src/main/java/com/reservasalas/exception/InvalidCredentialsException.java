package com.reservasalas.exception;

/**
 * Se lanza cuando el login falla, sin distinguir si el email no existe o si la contraseña es
 * incorrecta.
 */
public final class InvalidCredentialsException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con el mensaje genérico de credenciales no válidas.
     */
    public InvalidCredentialsException() {
        super("El email o la contraseña no son correctos");
    }
}
