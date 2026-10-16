package com.reservasalas.exception;

/**
 * Se lanza cuando la invitación no existe, no pertenece a la reserva indicada o no es del usuario
 * de la sesión. No se distingue el motivo para no revelar invitaciones ajenas.
 */
public final class InvitationNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción para la invitación indicada.
     *
     * @param invitationId identificador de la invitación
     */
    public InvitationNotFoundException(Long invitationId) {
        super("No existe la invitación " + invitationId);
    }
}
