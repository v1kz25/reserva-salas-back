package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sala de reuniones que se puede reservar. El nombre se guarda en forma Unicode NFC y sin espacios
 * en los extremos, y es único sin distinguir mayúsculas: la columna {@code NAME_KEY} guarda su forma
 * normalizada y lleva la restricción única, de modo que la base de datos también lo garantiza ante
 * altas concurrentes.
 */
@Entity
@Table(name = "ROOMS",
        uniqueConstraints = @UniqueConstraint(name = Room.UNIQUE_NAME_CONSTRAINT, columnNames = "NAME_KEY"))
public class Room {

    /** Nombre de la restricción única sobre el nombre normalizado. */
    public static final String UNIQUE_NAME_CONSTRAINT = "UK_ROOMS_NAME_KEY";

    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Longitud de la clave normalizada: el doble que la del nombre, porque pasar a minúsculas puede
     * alargar algunos caracteres (p. ej. «İ» pasa a «i» más un punto combinable).
     */
    private static final int MAX_NAME_KEY_LENGTH = 2 * MAX_NAME_LENGTH;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "NAME_KEY", nullable = false, length = MAX_NAME_KEY_LENGTH)
    private String nameKey;

    @Column(name = "CAPACITY", nullable = false)
    private int capacity;

    @Column(name = "FLOOR_NUMBER", nullable = false)
    private int floor;

    protected Room() {
        // Requerido por JPA
    }

    /**
     * Crea una sala nueva, aún sin persistir. El nombre se guarda sin espacios en los extremos.
     *
     * @param name     nombre único de la sala
     * @param capacity número máximo de personas
     * @param floor    planta en la que está (negativa para sótanos)
     */
    public Room(String name, int capacity, int floor) {
        assign(name, capacity, floor);
    }

    /**
     * Nombre tal y como se guarda: en forma Unicode NFC (para que una «á» compuesta y otra
     * descompuesta sean el mismo texto), con cada secuencia de espacios en blanco reducida a un
     * único espacio y sin espacios en los extremos.
     *
     * @param name nombre de la sala
     * @return el nombre limpio, o {@code null} si era nulo
     */
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String nfc = Normalizer.normalize(name, Normalizer.Form.NFC);
        return WHITESPACE_RUN.matcher(nfc).replaceAll(" ").strip();
    }

    /**
     * Forma normalizada de un nombre de sala con la que se comprueba la unicidad: el nombre limpio
     * de {@link #cleanName(String)} en minúsculas.
     *
     * @param name nombre de la sala
     * @return el nombre normalizado, o {@code null} si era nulo
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return cleanName(name).toLowerCase(Locale.ROOT);
    }

    /**
     * Sustituye los datos de la sala. El nombre se guarda sin espacios en los extremos.
     *
     * @param newName     nombre único de la sala
     * @param newCapacity número máximo de personas
     * @param newFloor    planta en la que está (negativa para sótanos)
     */
    public void update(String newName, int newCapacity, int newFloor) {
        assign(newName, newCapacity, newFloor);
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

    /**
     * Asigna los datos de la sala recortando el nombre y recalculando su forma normalizada. Es
     * privado para poder usarlo desde el constructor sin llamar a un método sobrescribible.
     */
    private void assign(String newName, int newCapacity, int newFloor) {
        this.name = cleanName(newName);
        this.nameKey = normalizeName(newName);
        this.capacity = newCapacity;
        this.floor = newFloor;
    }
}
