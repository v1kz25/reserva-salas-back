package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Relación de amistad entre dos usuarios: nace como solicitud pendiente y, al aceptarla el
 * destinatario, pasa a ser una amistad. Rechazar o retirar la solicitud y eliminar la amistad borran
 * la fila.
 *
 * <p>Las columnas {@code USER_LOW_ID} y {@code USER_HIGH_ID} guardan el par de usuarios ordenado
 * (el menor identificador primero) y llevan una restricción única. Así la base de datos garantiza
 * que entre dos usuarios haya como mucho una fila, sea una solicitud en cualquier sentido o una
 * amistad, también ante envíos simultáneos de A a B y de B a A.
 */
@Entity
@Table(name = "FRIEND_REQUESTS",
        uniqueConstraints = @UniqueConstraint(name = FriendRequest.UNIQUE_PAIR_CONSTRAINT,
                columnNames = {"USER_LOW_ID", "USER_HIGH_ID"}),
        indexes = {
                @Index(name = "IX_FRIEND_REQUESTS_REQUESTER", columnList = "REQUESTER_ID"),
                @Index(name = "IX_FRIEND_REQUESTS_RECIPIENT", columnList = "RECIPIENT_ID"),
                @Index(name = "IX_FRIEND_REQUESTS_USER_HIGH", columnList = "USER_HIGH_ID")
        })
public class FriendRequest {

    /** Nombre de la restricción única sobre el par ordenado de usuarios. */
    public static final String UNIQUE_PAIR_CONSTRAINT = "UK_FRIEND_REQUESTS_PAIR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REQUESTER_ID", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RECIPIENT_ID", nullable = false)
    private User recipient;

    @Column(name = "USER_LOW_ID", nullable = false)
    private Long userLowId;

    @Column(name = "USER_HIGH_ID", nullable = false)
    private Long userHighId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private FriendRequestStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "ACCEPTED_AT")
    private Instant acceptedAt;

    protected FriendRequest() {
        // Requerido por JPA
    }

    /**
     * Crea una solicitud pendiente, aún sin persistir. Los dos usuarios deben estar persistidos y
     * ser distintos.
     *
     * @param requester usuario que envía la solicitud
     * @param recipient usuario al que va dirigida
     * @param createdAt instante de envío
     */
    public FriendRequest(User requester, User recipient, Instant createdAt) {
        this.requester = requester;
        this.recipient = recipient;
        this.userLowId = Math.min(requester.getId(), recipient.getId());
        this.userHighId = Math.max(requester.getId(), recipient.getId());
        this.status = FriendRequestStatus.PENDING;
        this.createdAt = createdAt;
    }

    /**
     * Acepta la solicitud: desde ese instante los dos usuarios son amigos.
     *
     * @param when instante de aceptación
     */
    public void accept(Instant when) {
        this.status = FriendRequestStatus.ACCEPTED;
        this.acceptedAt = when;
    }

    /**
     * Indica si la solicitud sigue pendiente.
     *
     * @return {@code true} si aún no se ha aceptado
     */
    public boolean isPending() {
        return status == FriendRequestStatus.PENDING;
    }

    /**
     * Identificador de la solicitud.
     *
     * @return el identificador, o {@code null} si aún no se ha persistido
     */
    public Long getId() {
        return id;
    }

    /**
     * Usuario que envió la solicitud.
     *
     * @return el solicitante
     */
    public User getRequester() {
        return requester;
    }

    /**
     * Usuario al que se envió la solicitud.
     *
     * @return el destinatario
     */
    public User getRecipient() {
        return recipient;
    }

    /**
     * Estado de la solicitud.
     *
     * @return el estado
     */
    public FriendRequestStatus getStatus() {
        return status;
    }

    /**
     * Instante en el que se envió la solicitud.
     *
     * @return el instante de envío
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Instante en el que se aceptó la solicitud.
     *
     * @return el instante de aceptación, o {@code null} si sigue pendiente
     */
    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
