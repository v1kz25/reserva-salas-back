package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.entity.AppSetting;
import com.reservasalas.repository.AppSettingRepository;
import com.reservasalas.support.TestUsers;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

/**
 * Un contexto cuyo repositorio de ajustes ya devuelve una zona guardada la aplica al arrancar, sin
 * ningún PUT, y la sirve desde la primera petición.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:zona-arranque-${random.uuid}",
        "app.timezone=Europe/Madrid"})
@WebAppConfiguration
@Import(ZonaGuardadaAlArrancarIntegrationTest.StoredZoneConfig.class)
class ZonaGuardadaAlArrancarIntegrationTest {

    @TestConfiguration
    static class StoredZoneConfig {
        @Bean
        @Primary
        AppSettingRepository storedSettings() {
            AppSettingRepository repository = mock(AppSettingRepository.class);
            when(repository.findById(AppSetting.TIME_ZONE_KEY))
                    .thenReturn(Optional.of(new AppSetting(AppSetting.TIME_ZONE_KEY, "Asia/Tokyo")));
            return repository;
        }
    }

    @Autowired
    private CurrentTimeZone currentTimeZone;
    @Autowired
    private WebApplicationContext context;

    @Test
    void laZonaGuardadaSustituyeALaDeAppTimezoneAlArrancar() throws Exception {
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        TestUsers.mockMvcAs(context, TestUsers.ANA)
                .perform(get("/api/config/zona-horaria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("Asia/Tokyo"));
    }
}
