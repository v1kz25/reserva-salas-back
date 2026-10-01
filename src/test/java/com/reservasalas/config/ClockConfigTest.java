package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ClockConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ClockConfig.class);

    @Test
    void zonaInvalidaImpideArrancarElContexto() {
        runner.withPropertyValues("app.timezone=Marte/Olympus")
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Zona horaria no válida en app.timezone")
                        .hasStackTraceContaining("Marte/Olympus"));
    }

    @Test
    void zonaConFormatoIncorrectoImpideArrancarElContexto() {
        runner.withPropertyValues("app.timezone=no es una zona")
                .run(ctx -> assertThat(ctx.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("Zona horaria no válida en app.timezone"));
    }

    @Test
    void zonaValidaCreaElRelojEnEsaZona() {
        runner.withPropertyValues("app.timezone=Asia/Tokyo")
                .run(ctx -> assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Tokyo")));
    }

    @Test
    void zonaVaciaImpideArrancarElContexto() {
        runner.withPropertyValues("app.timezone=")
                .run(ctx -> assertThat(ctx.getStartupFailure())
                        .isNotNull()
                        .hasStackTraceContaining("Zona horaria no válida en app.timezone"));
    }

    @Test
    void zonaConEspaciosAlrededorSeNormaliza() {
        runner.withInitializer(ctx -> ctx.getEnvironment()
                        .getPropertySources()
                        .addFirst(new MapPropertySource("test", Map.of("app.timezone", "  Europe/Madrid  "))))
                .run(ctx -> assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Madrid")));
    }

    @Test
    void sinZonaConfiguradaUsaEuropaMadridAunqueExistaAppTimezoneEnElEntorno() {
        // Carga el application.yml real y vacía las variables de entorno para no depender de la máquina.
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(ctx -> ctx.getEnvironment()
                        .getPropertySources()
                        .replace(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                new MapPropertySource(
                                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of())))
                .withUserConfiguration(ClockConfig.class)
                .run(ctx -> assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Madrid")));
    }
}
