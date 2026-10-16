package com.reservasalas.exception;

/**
 * Se lanza cuando se ha superado el límite de intentos fallidos de inicio de sesión para un email
 * desde una IP. El mensaje es el mismo exista o no el email.
 */
public final class TooManyLoginAttemptsException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    /**
     * Crea la excepción con el mensaje genérico de demasiados intentos.
     *
     * @param retryAfterSeconds segundos que hay que esperar antes de volver a intentarlo; al menos 1
     */
    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos de inicio de sesión. Vuelve a intentarlo más tarde");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Segundos que hay que esperar antes de volver a intentarlo.
     *
     * @return los segundos, redondeados hacia arriba; al menos 1
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
