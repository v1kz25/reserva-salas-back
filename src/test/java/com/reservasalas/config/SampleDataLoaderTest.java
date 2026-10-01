package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.repository.RoomRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

class SampleDataLoaderTest {

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sample-dev-${random.uuid}")
    @ActiveProfiles("dev")
    class ConPerfilDev {
        @Autowired
        RoomRepository rooms;

        @Test
        void cargaEntreTresYCuatroSalas() {
            assertThat(rooms.count()).isBetween(3L, 4L);
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sample-sin-dev-${random.uuid}")
    class SinPerfilDev {
        @Autowired
        RoomRepository rooms;
        @Autowired
        ApplicationContext ctx;

        @Test
        void noCargaSalas() {
            assertThat(rooms.count()).isZero();
            assertThat(ctx.getBeanNamesForType(SampleDataLoader.class)).isEmpty();
        }
    }
}
