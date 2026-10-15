package com.reservasalas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.entity.Room;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de alta o modificación de una sala ({@code SalaRequest} en el contrato). El identificador
 * no viaja en la petición: lo genera el servidor. El nombre llega ya sin espacios en los extremos y en
 * forma Unicode NFC, de modo que las validaciones se aplican al nombre que se guardará. Los campos desconocidos (p. ej.
 * un {@code id}) se ignoran.
 *
 * @param name     nombre de la sala, de 1 a 100 caracteres sin contar los espacios de los extremos
 * @param capacity número máximo de personas, al menos 1
 * @param floor    planta en la que está; puede ser negativa (sótanos)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomRequest(
        @JsonProperty("nombre")
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "no puede superar los 100 caracteres")
        String name,

        @JsonProperty("capacidad")
        @NotNull(message = "es obligatoria")
        @Min(value = 1, message = "debe ser al menos 1")
        Integer capacity,

        @JsonProperty("planta")
        @NotNull(message = "es obligatoria")
        Integer floor) {

    /**
     * Deja el nombre como se guardará (forma Unicode NFC y sin espacios en los extremos), para que
     * la validación de longitud se aplique sobre ese valor.
     */
    public RoomRequest {
        name = Room.cleanName(name);
    }
}
