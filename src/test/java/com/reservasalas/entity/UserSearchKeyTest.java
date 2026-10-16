package com.reservasalas.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserSearchKeyTest {

    @Test
    void quitaAcentosYPasaAMinusculas() {
        assertThat(User.searchKey("José ÁÉÍÓÚ")).isEqualTo("jose aeiou");
    }

    @Test
    void descomponeEnNfdAsiQueLaFormaPrecompuestaYLaDescompuestaDanLoMismo() {
        assertThat(User.searchKey("\u00e9")).isEqualTo(User.searchKey("e\u0301")).isEqualTo("e");
    }

    @Test
    void laEnieTambienPierdeLaTilde() {
        assertThat(User.searchKey("Ñandú PEÑA")).isEqualTo("nandu pena");
    }

    @Test
    void reduceLosEspaciosMultiplesYQuitaLosDeLosExtremos() {
        assertThat(User.searchKey("  Ana \t  María  López  ")).isEqualTo("ana maria lopez");
    }

    @Test
    void conNuloDevuelveNulo() {
        assertThat(User.searchKey(null)).isNull();
    }

    @Test
    void conSoloMarcasDiacriticasDevuelveCadenaVacia() {
        assertThat(User.searchKey("\u0301\u0301")).isEmpty();
        assertThat(User.searchKey("")).isEmpty();
    }
}
