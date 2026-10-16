package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Validación al arrancar de las propiedades {@code app.login.*}.
 */
class LoginAttemptPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(LoginAttemptProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    private static String[] props(String maxAttempts, String windowMinutes, String maxEntries) {
        return new String[] {
                "app.login.max-attempts=" + maxAttempts,
                "app.login.window-minutes=" + windowMinutes,
                "app.login.max-entries=" + maxEntries};
    }

    @Test
    void valoresValidosArrancan() {
        runner.withPropertyValues(props("5", "15", "10000"))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    LoginAttemptProperties bound = ctx.getBean(LoginAttemptProperties.class);
                    assertThat(bound.maxAttempts()).isEqualTo(5);
                    assertThat(bound.windowMinutes()).isEqualTo(15);
                    assertThat(bound.maxEntries()).isEqualTo(10000);
                });
    }

    @Test
    void losMaximosPermitidosArrancanYSeEnlazan() {
        runner.withPropertyValues(props("1000", "1440", "1000000"))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    LoginAttemptProperties bound = ctx.getBean(LoginAttemptProperties.class);
                    assertThat(bound.maxAttempts()).isEqualTo(1000);
                    assertThat(bound.windowMinutes()).isEqualTo(1440);
                    assertThat(bound.maxEntries()).isEqualTo(1_000_000);
                });
    }

    @Test
    void windowMinutesPorEncimaDelMaximoHaceFallarElArranque() {
        runner.withPropertyValues(props("5", "1441", "10000"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void maxEntriesPorEncimaDelMaximoHaceFallarElArranque() {
        runner.withPropertyValues(props("5", "15", "1000001"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-100"})
    void maxAttemptsNoValidoHaceFallarElArranque(String value) {
        runner.withPropertyValues(props(value, "15", "10000"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-15"})
    void windowMinutesNoValidoHaceFallarElArranque(String value) {
        runner.withPropertyValues(props("5", value, "10000"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-10000"})
    void maxEntriesNoValidoHaceFallarElArranque(String value) {
        runner.withPropertyValues(props("5", "15", value))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1001", "abc"})
    void maxAttemptsFueraDeRangoONoNumericoFalla(String value) {
        runner.withPropertyValues(props(value, "15", "10000"))
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
