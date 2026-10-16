package com.reservasalas.exception;

/**
 * Se lanza cuando una invitación no se puede crear, aceptar o rechazar por el estado actual de la
 * reserva, de la invitación o de la amistad.
 */
public final class InvitationConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    private InvitationConflictException(String message) {
        super(message);
    }

    /**
     * El usuario al que se invita no es amigo confirmado del organizador, no existe o está
     * desactivado. No se distingue el motivo para no revelar qué usuarios existen.
     *
     * @return la excepción
     */
    public static InvitationConflictException notFriend() {
        return new InvitationConflictException("Solo puedes invitar a tus amigos");
    }

    /**
     * El usuario ya tiene una invitación pendiente o aceptada a la reserva.
     *
     * @return la excepción
     */
    public static InvitationConflictException alreadyInvited() {
        return new InvitationConflictException("Este usuario ya está invitado a la reserva");
    }

    /**
     * Con la nueva invitación, el organizador más las invitaciones pendientes y aceptadas superarían
     * la capacidad de la sala.
     *
     * @param capacity capacidad de la sala
     * @return la excepción
     */
    public static InvitationConflictException capacityExceeded(int capacity) {
        return new InvitationConflictException(
                "La sala tiene capacidad para " + capacity + " personas y ya está completa");
    }

    /**
     * La reserva ya ha terminado.
     *
     * @return la excepción
     */
    public static InvitationConflictException reservationEnded() {
        return new InvitationConflictException("La reserva ya ha pasado");
    }

    /**
     * Se intenta aceptar una invitación que ya no está pendiente.
     *
     * @return la excepción
     */
    public static InvitationConflictException notPending() {
        return new InvitationConflictException("La invitación ya no está pendiente");
    }

    /**
     * Se intenta rechazar una invitación que ya estaba rechazada.
     *
     * @return la excepción
     */
    public static InvitationConflictException alreadyRejected() {
        return new InvitationConflictException("La invitación ya estaba rechazada");
    }
}
