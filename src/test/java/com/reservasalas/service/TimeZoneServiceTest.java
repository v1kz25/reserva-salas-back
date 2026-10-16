package com.reservasalas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.TimeZoneDto;
import com.reservasalas.entity.AppSetting;
import com.reservasalas.exception.FieldValidationException;
import com.reservasalas.exception.InvalidTimeZoneException;
import com.reservasalas.repository.AppSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

class TimeZoneServiceTest {

    private AppSettingRepository repository;
    private CurrentTimeZone current;
    private TimeZoneService service;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneId.of("Europe/Madrid"));
        current = new CurrentTimeZone(clock);
        service = new TimeZoneService(repository, current);
        logger = (Logger) LoggerFactory.getLogger(TimeZoneService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void getDevuelveLaZonaVigente() {
        assertThat(service.get()).isEqualTo(new TimeZoneDto("Europe/Madrid"));
    }

    @Test
    void changeGuardaLaZonaNormalizadaYLaAplica() {
        TimeZoneDto result = service.change(new TimeZoneDto("  UTC "));

        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo(AppSetting.TIME_ZONE_KEY);
        assertThat(saved.getValue().getValue()).isEqualTo("UTC");
        assertThat(result.timeZone()).isEqualTo("UTC");
        assertThat(current.zone().getId()).isEqualTo("UTC");
        assertThat(current.today()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void changeConZonaInvalidaLanzaExcepcionDeCampoYNoGuardaNiCambia() {
        for (String id : new String[] {"Invalid/City", "+02:00", "", "  ", null, "europe/madrid", "UTC+2"}) {
            assertThatThrownBy(() -> service.change(new TimeZoneDto(id)))
                    .isInstanceOf(InvalidTimeZoneException.class)
                    .isInstanceOf(FieldValidationException.class)
                    .extracting(e -> ((FieldValidationException) e).getField())
                    .isEqualTo("zonaHoraria");
        }
        verify(repository, never()).save(any());
        assertThat(current.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void siFallaElGuardadoNoSeAplicaLaZona() {
        when(repository.save(any())).thenThrow(new IllegalStateException("BD caída"));

        assertThatThrownBy(() -> service.change(new TimeZoneDto("UTC"))).isInstanceOf(IllegalStateException.class);

        assertThat(current.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void alArrancarSeAplicaLaZonaGuardada() {
        when(repository.findById(AppSetting.TIME_ZONE_KEY))
                .thenReturn(Optional.of(new AppSetting(AppSetting.TIME_ZONE_KEY, "America/New_York")));

        service.afterSingletonsInstantiated();

        assertThat(current.zone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(current.today()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void alArrancarSinZonaGuardadaSeMantieneLaDeAppTimezone() {
        when(repository.findById(AppSetting.TIME_ZONE_KEY)).thenReturn(Optional.empty());

        service.afterSingletonsInstantiated();

        assertThat(current.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void alArrancarUnaZonaGuardadaNoValidaSeIgnoraConUnAviso() {
        for (String bad : new String[] {"Invalid/City", "+02:00", "europe/madrid", ""}) {
            logs.list.clear();
            when(repository.findById(AppSetting.TIME_ZONE_KEY))
                    .thenReturn(Optional.of(new AppSetting(AppSetting.TIME_ZONE_KEY, bad)));

            service.afterSingletonsInstantiated();

            assertThat(current.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
            assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.WARN);
        }
    }

    @Test
    void alArrancarSiFallaLaLecturaDeLaBaseDeDatosLaExcepcionSePropaga() {
        when(repository.findById(AppSetting.TIME_ZONE_KEY))
                .thenThrow(new DataAccessResourceFailureException("BD caída"));

        assertThatThrownBy(() -> service.afterSingletonsInstantiated())
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        assertThat(current.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    }
}
