package com.reservasalas.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el reloj de la aplicación, fijado a la zona horaria configurada, para calcular «hoy» y
 * «mañana» de forma independiente de la zona del servidor y poder fijar la hora en los tests.
 */
@Configuration
public class ClockConfig {

    /**
     * Reloj del sistema en la zona horaria definida en {@code app.timezone}.
     *
     * @param timezone identificador de zona horaria IANA, p. ej. {@code Europe/Madrid}
     * @return el reloj en la zona configurada
     * @throws IllegalStateException si {@code app.timezone} no es una zona horaria válida, para que la
     *     aplicación no arranque con una zona distinta de la esperada
     */
    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(parseZone(timezone));
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone.strip());
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                    "Zona horaria no válida en app.timezone (APP_TIMEZONE): '" + timezone
                            + "'. Usa un identificador IANA, p. ej. Europe/Madrid",
                    e);
        }
    }
}
