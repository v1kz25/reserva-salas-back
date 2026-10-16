package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CurrentTimeZoneTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneId.of("Europe/Madrid"));

    @Test
    void arrancaConLaZonaDelRelojYCalculaHoy() {
        CurrentTimeZone zone = new CurrentTimeZone(clock);
        assertThat(zone.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(zone.today()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void cambiarLaZonaCambiaHoyEnCaliente() {
        CurrentTimeZone zone = new CurrentTimeZone(clock);
        zone.change(ZoneId.of("America/New_York"));
        assertThat(zone.today()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void cambiarANuloLanzaExcepcion() {
        assertThatThrownBy(() -> new CurrentTimeZone(clock).change(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseRegionIdAdmiteRegionesYRecortaEspacios() {
        assertThat(CurrentTimeZone.parseRegionId("Europe/Madrid")).contains(ZoneId.of("Europe/Madrid"));
        assertThat(CurrentTimeZone.parseRegionId("  UTC ")).contains(ZoneId.of("UTC"));
    }

    @Test
    void parseRegionIdAdmiteUtcYRegionesDeTresNiveles() {
        assertThat(CurrentTimeZone.parseRegionId("UTC")).contains(ZoneId.of("UTC"));
        assertThat(CurrentTimeZone.parseRegionId("America/Argentina/Buenos_Aires"))
                .contains(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void parseRegionIdRechazaInvalidos() {
        for (String id : new String[] {null, "", "   ", "Invalid/City", "+02:00", "UTC+2", "GMT+1", "Z",
                "europe/madrid", "EUROPE/MADRID", "Etc/GMT+1", "Etc/GMT-14",
                "SystemV/AST4", "GMT0", "EST5EDT"}) {
            assertThat(CurrentTimeZone.parseRegionId(id)).as(String.valueOf(id)).isEmpty();
        }
    }
}
