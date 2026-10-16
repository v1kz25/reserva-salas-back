package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Invitación de un amigo del organizador a una reserva.
 *
 * <p>Hay como mucho una invitación por reserva y usuario; la base de datos lo garantiza con una
 * restricción única. Si el invitado la rechaza, la fila se conserva como rechazada y el organizador
 * puede volver a invitarle (pasa de nuevo a pendiente).
 *
 * <p>La clave ajena hacia la reserva lleva {@code ON DELETE CASCADE}: al borrar una reserva, ya sea
 * al cancelarla o al eliminar su sala, la base de datos borra a la vez sus invitaciones, también
 * con los borrados masivos por consulta, que no pasan por el ciclo de vida de JPA.
 */
@Entity
@Table(name = "INVITATIONS",
        uniqueConstraints = @UniqueConstraint(name = Invitation.UNIQUE_RESERVATION_USER_CONSTRAINT,
                columnNames = {"RESERVATION_ID", "USER_ID"}),
        indexes = @Index(name = "IX_INVITATIONS_USER", columnList = "USER_ID"))
public class Invitation {

    /** Nombre de la restricción única sobre la reserva y el usuario invitado. */
    public static final String UNIQUE_RESERVATION_USER_CONSTRAINT = "UK_INVITATIONS_RESERVATION_USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RESERVATION_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_INVITATIONS_RESERVATION"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_INVITATIONS_USER"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private InvitationStatus status;

    protected Invitation() {
        // Requerido por JPA
    }

    /**
     * Crea una invitación pendiente, aún sin persistir.
     *
     * @param reservation reserva a la que se invita
     * @param user        usuario invitado
     */
    public Invitation(Reservation reservation, User user) {
        this.reservation = reservation;
        this.user = user;
        this.status = InvitationStatus.PENDING;
    }

    /**
     * Vuelve a invitar a un usuario que había rechazado la invitación: pasa de nuevo a pendiente.
     */
    public void reinvite() {
        this.status = InvitationStatus.PENDING;
    }

    /**
     * Acepta la invitación.
     */
    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
    }

    /**
     * Rechaza la invitación, esté pendiente o aceptada (dejar de asistir).
     */
    public void reject() {
        this.status = InvitationStatus.REJECTED;
    }

    /**
     * Identificador de la invitación.
     *
     * @return el identificador, o {@code null} si aún no se ha persistido
     */
    public Long getId() {
        return id;
    }

    /**
     * Reserva a la que se invita. Se carga de forma perezosa; su id está disponible sin consultar la
     * base de datos.
     *
     * @return la reserva
     */
    public Reservation getReservation() {
        return reservation;
    }

    /**
     * Usuario invitado. Se carga de forma perezosa; su id está disponible sin consultar la base de
     * datos.
     *
     * @return el invitado
     */
    public User getUser() {
        return user;
    }

    /**
     * Estado de la invitación.
     *
     * @return el estado
     */
    public InvitationStatus getStatus() {
        return status;
    }
}
