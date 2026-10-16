package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Reserva de una sala en una franja horaria de un día.
 */
@Entity
@Table(name = "RESERVATIONS",
        indexes = @Index(name = "IDX_RESERVATIONS_ROOM_DATE", columnList = "ROOM_ID, RESERVATION_DATE"))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ROOM_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_RESERVATIONS_ROOM"))
    private Room room;

    @Column(name = "RESERVATION_DATE", nullable = false)
    private LocalDate date;

    @Column(name = "START_TIME", nullable = false)
    private LocalTime startTime;

    @Column(name = "END_TIME", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_RESERVATIONS_USER"))
    private User user;

    @Column(name = "REASON", nullable = false, length = 255)
    private String reason;

    protected Reservation() {
        // Requerido por JPA
    }

    /**
     * Crea una reserva nueva, aún sin persistir.
     *
     * @param room        sala reservada
     * @param date        día de la reserva
     * @param startTime   hora de inicio
     * @param endTime     hora de fin, posterior a la de inicio
     * @param user        usuario que hace la reserva y queda como responsable
     * @param reason      motivo de la reserva
     */
    public Reservation(Room room, LocalDate date, LocalTime startTime, LocalTime endTime,
                       User user, String reason) {
        this.room = room;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.user = user;
        this.reason = reason;
    }

    /**
     * Indica si el usuario es el organizador de la reserva, es decir, quien la hizo. No inicializa
     * el usuario.
     *
     * @param userId identificador del usuario
     * @return {@code true} si la reserva es suya
     */
    public boolean isOrganizedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /**
     * Indica si la reserva ya ha terminado: su día y hora de fin no son posteriores al instante
     * indicado. Una reserva en curso aún no ha terminado.
     *
     * @param now fecha y hora actuales en la zona horaria vigente
     * @return {@code true} si la reserva ya ha pasado
     */
    public boolean hasEndedBy(LocalDateTime now) {
        return !LocalDateTime.of(date, endTime).isAfter(now);
    }

    /**
     * Identificador de la reserva.
     *
     * @return el identificador, o {@code null} si aún no se ha persistido
     */
    public Long getId() {
        return id;
    }

    /**
     * Sala reservada. Se carga de forma perezosa; su id está disponible sin consultar la base de datos.
     *
     * @return la sala
     */
    public Room getRoom() {
        return room;
    }

    /**
     * Día de la reserva.
     *
     * @return la fecha
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Hora de inicio de la reserva.
     *
     * @return la hora de inicio
     */
    public LocalTime getStartTime() {
        return startTime;
    }

    /**
     * Hora de fin de la reserva.
     *
     * @return la hora de fin
     */
    public LocalTime getEndTime() {
        return endTime;
    }

    /**
     * Usuario que hizo la reserva y es su responsable. Se carga de forma perezosa; su id está
     * disponible sin consultar la base de datos.
     *
     * @return el usuario
     */
    public User getUser() {
        return user;
    }

    /**
     * Motivo de la reserva.
     *
     * @return el motivo
     */
    public String getReason() {
        return reason;
    }
}
