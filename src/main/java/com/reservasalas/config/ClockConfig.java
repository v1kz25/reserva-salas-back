package com.reservasalas.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el reloj de la aplicación, en la zona horaria de arranque ({@code APP_TIMEZONE}), para
 * poder fijar la hora en los tests. «Hoy» se calcula con {@link CurrentTimeZone}, que parte de la
 * zona de este reloj y puede cambiarse en caliente.
 */
@Configuration
public class ClockConfig {

    /**
     * Reloj del sistema en la zona horaria definida en {@code app.timezone}.
     *
     * @param timezone identificador IANA de región, p. ej. {@code Europe/Madrid}; no se admiten
     *                 desfases fijos como {@code +02:00}
     * @return el reloj en la zona configurada
     * @throws IllegalStateException si {@code app.timezone} no es una zona horaria válida, para que la
     *     aplicación no arranque con una zona distinta de la esperada
     */
    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(parseZone(timezone));
    }

    private static ZoneId parseZone(String timezone) {
        return CurrentTimeZone.parseRegionId(timezone)
                .orElseThrow(() -> new IllegalStateException(
                        "Zona horaria no válida en app.timezone (APP_TIMEZONE): '" + timezone
                                + "'. Usa un identificador IANA de región, p. ej. Europe/Madrid"));
    }
}
