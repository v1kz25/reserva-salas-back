package com.reservasalas.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomNameKeyTest {

    private static final String A_COMPUESTA = "\u00e1";
    private static final String A_DESCOMPUESTA = "a\u0301";

    @Test
    void normalizeNameRecortaYPasaAMinusculas() {
        assertThat(Room.normalizeName("  Sala Grande \t")).isEqualTo("sala grande");
        assertThat(Room.normalizeName("SALA")).isEqualTo(Room.normalizeName("sala"));
    }

    @Test
    void normalizeNameDeNullEsNull() {
        assertThat(Room.normalizeName(null)).isNull();
    }

    @Test
    void normalizeNameDaLaMismaClaveParaAcentoCompuestoYDescompuesto() {
        assertThat(Room.normalizeName("Sal" + A_COMPUESTA + "n"))
                .isEqualTo(Room.normalizeName("Sal" + A_DESCOMPUESTA + "n"));
        assertThat(Room.normalizeName("SAL" + "\u00c1" + "N"))
                .isEqualTo(Room.normalizeName("sal" + A_DESCOMPUESTA + "n"));
    }

    @Test
    void salaCreadaConFormasEquivalentesTieneElMismoNombreGuardadoRecortado() {
        Room room = new Room("  Sal" + A_COMPUESTA + "n  ", 4, 1);
        assertThat(room.getName()).isEqualTo("Sal" + A_COMPUESTA + "n").doesNotStartWith(" ");
    }

    @Test
    void cleanNameReduceEspaciosInternosYRecorta() {
        assertThat(Room.cleanName("  SALA   NUEVA ")).isEqualTo("SALA NUEVA");
        assertThat(Room.cleanName(null)).isNull();
    }

    @Test
    void cleanNameTratamosTabuladorYEspacioDeNoSeparacionComoEspacios() {
        assertThat(Room.cleanName("Sala\t\tNueva")).isEqualTo("Sala Nueva");
        assertThat(Room.cleanName("Sala\u00a0\u00a0Nueva")).isEqualTo("Sala Nueva");
        assertThat(Room.cleanName("\u00a0Sala \t\u00a0Nueva\u00a0")).isEqualTo("Sala Nueva");
    }

    @Test
    void normalizeNameUnificaEspaciosInternosYMayusculas() {
        assertThat(Room.normalizeName("  SALA   NUEVA ")).isEqualTo("sala nueva");
        assertThat(Room.normalizeName("Sala\u00a0Nueva")).isEqualTo(Room.normalizeName("sala\tnueva"));
    }

    @Test
    void salaGuardaElNombreSinEspaciosDobles() {
        assertThat(new Room("  SALA   NUEVA ", 4, 1).getName()).isEqualTo("SALA NUEVA");
    }
}
