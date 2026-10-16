package com.reservasalas.entity;

/**
 * Estado de una solicitud de amistad. Una solicitud rechazada o retirada no tiene estado propio:
 * se elimina, para que se pueda volver a enviar.
 */
public enum FriendRequestStatus {

    /** Enviada y a la espera de que el destinatario la acepte o la rechace. */
    PENDING,

    /** Aceptada: los dos usuarios son amigos. */
    ACCEPTED
}
