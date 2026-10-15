package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Room;

/**
 * Sala tal y como se expone en la API ({@code Sala} en el contrato).
 *
 * @param id       identificador
 * @param name     nombre de la sala
 * @param capacity número máximo de personas
 * @param floor    planta, negativa para sótanos
 */
public record RoomDto(
        @JsonProperty("id") Long id,
        @JsonProperty("nombre") String name,
        @JsonProperty("capacidad") int capacity,
        @JsonProperty("planta") int floor) {

    /**
     * Construye el DTO a partir de la entidad.
     *
     * @param room sala persistida
     * @return DTO equivalente
     */
    public static RoomDto from(Room room) {
        return new RoomDto(room.getId(), room.getName(), room.getCapacity(), room.getFloor());
    }
}
