package com.reservasalas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Room;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RoomRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void recortaElNombre() {
        assertThat(new RoomRequest("  Sala  ", 3, 1).name()).isEqualTo("Sala");
    }

    @Test
    void nombreNuloSeConservaYFallaLaValidacion() {
        RoomRequest request = new RoomRequest(null, 3, 1);
        assertThat(request.name()).isNull();
        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    void nombreSoloEspaciosFallaLaValidacion() {
        assertThat(validator.validate(new RoomRequest("   ", 3, 1))).hasSize(1);
    }

    @Test
    void nombreDeCienCaracteresMasEspaciosEsValidoPeroCientoUnoNo() {
        assertThat(validator.validate(new RoomRequest(" " + "a".repeat(100) + " ", 3, 1))).isEmpty();
        assertThat(validator.validate(new RoomRequest("a".repeat(101), 3, 1))).hasSize(1);
    }

    @Test
    void capacidadYPlantaSeValidan() {
        assertThat(validator.validate(new RoomRequest("S", 0, 1))).hasSize(1);
        assertThat(validator.validate(new RoomRequest("S", null, null))).hasSize(2);
        assertThat(validator.validate(new RoomRequest("S", 1, -3))).isEmpty();
    }

    @Test
    void formasUnicodeEquivalentesDanLaMismaClave() {
        RoomRequest composed = new RoomRequest("Sal\u00e1n", 3, 1);
        RoomRequest decomposed = new RoomRequest("sala\u0301n", 3, 1);
        assertThat(Room.normalizeName(composed.name())).isEqualTo(Room.normalizeName(decomposed.name()));
    }
}
