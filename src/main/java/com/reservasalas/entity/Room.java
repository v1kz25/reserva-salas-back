package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Sala de reuniones que se puede reservar.
 */
@Entity
@Table(name = "ROOMS", uniqueConstraints = @UniqueConstraint(name = "UK_ROOMS_NAME", columnNames = "NAME"))
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "CAPACITY", nullable = false)
    private int capacity;

    @Column(name = "FLOOR_NUMBER", nullable = false)
    private int floor;

    protected Room() {
        // Requerido por JPA
    }

    /**
     * Crea una sala nueva, aún sin persistir.
     *
     * @param name     nombre único de la sala
     * @param capacity número máximo de personas
     * @param floor    planta en la que está (negativa para sótanos)
     */
    public Room(String name, int capacity, int floor) {
        this.name = name;
        this.capacity = capacity;
        this.floor = floor;
    }

    /**
     * Identificador de la sala.
     *
     * @return el identificador, o {@code null} si aún no se ha persistido
     */
    public Long getId() {
        return id;
    }

    /**
     * Nombre único de la sala.
     *
     * @return el nombre
     */
    public String getName() {
        return name;
    }

    /**
     * Número máximo de personas que caben en la sala.
     *
     * @return la capacidad
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Planta en la que está la sala.
     *
     * @return la planta, negativa para sótanos
     */
    public int getFloor() {
        return floor;
    }
}
