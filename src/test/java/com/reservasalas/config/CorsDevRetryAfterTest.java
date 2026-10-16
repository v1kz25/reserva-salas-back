package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Con el perfil {@code dev}, el navegador del front puede leer {@code Retry-After} del 429.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cors-retry-${random.uuid}",
        "app.login.max-attempts=1"})
@ActiveProfiles("dev")
class CorsDevRetryAfterTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void elLoginBloqueadoExponeRetryAfterAlOrigenDelFront() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        MvcResult last = null;
        for (int i = 0; i < 2; i++) {
            last = mvc.perform(post("/api/auth/login").with(csrf())
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nadie@example.com\",\"password\":\"incorrecta\"}")).andReturn();
        }

        assertThat(last.getResponse().getStatus()).isEqualTo(429);
        assertThat(last.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(last.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .containsIgnoringCase("Retry-After");
    }
}
