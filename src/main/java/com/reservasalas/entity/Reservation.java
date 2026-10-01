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

    @Column(name = "RESPONSIBLE", nullable = false, length = 100)
    private String responsible;

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
     * @param responsible persona responsable de la reserva
     * @param reason      motivo de la reserva
     */
    public Reservation(Room room, LocalDate date, LocalTime startTime, LocalTime endTime,
                       String responsible, String reason) {
        this.room = room;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.responsible = responsible;
        this.reason = reason;
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
     * Persona responsable de la reserva.
     *
     * @return el responsable
     */
    public String getResponsible() {
        return responsible;
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
