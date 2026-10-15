package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * CORS con credenciales: solo con el perfil {@code dev} y solo para el origen del front local.
 */
class CorsDevTest {

    private static MvcResult preflight(WebApplicationContext context, String origin) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        return mvc.perform(options("/api/reservas")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andReturn();
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cors-dev-${random.uuid}")
    @ActiveProfiles("dev")
    class ConPerfilDev {
        @Autowired
        WebApplicationContext context;

        @Test
        void elPreflightDelFrontLocalNoDa401YPermiteCredenciales() throws Exception {
            MvcResult result = preflight(context, "http://localhost:4200");

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo("http://localhost:4200");
            assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                    .isEqualTo("true");
            assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                    .containsIgnoringCase("X-XSRF-TOKEN");
        }

        @Test
        void otroOrigenNoSePermite() throws Exception {
            MvcResult result = preflight(context, "http://malo.example.com");

            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cors-prod-${random.uuid}")
    class SinPerfilDev {
        @Autowired
        WebApplicationContext context;

        @Test
        void sinPerfilDevNoSeConcedeCors() throws Exception {
            MvcResult result = preflight(context, "http://localhost:4200");

            assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        }
    }
}
