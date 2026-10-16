package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Límite de intentos de login: 3 intentos por email e IP en una ventana de 10 minutos, con un reloj
 * que el test mueve a mano.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:login-limite-${random.uuid}",
        "app.login.max-attempts=3",
        "app.login.window-minutes=10",
        "app.login.max-entries=100"})
@ExtendWith(OutputCaptureExtension.class)
class LoginLimiteIntegrationTest {

    private static final int MAX = 3;
    private static final String PASSWORD = "clave-de-prueba-1";
    private static final String IP = "198.51.100.10";

    /** Reloj mutable que sustituye al de la aplicación. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2030-01-01T10:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Madrid");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration
    static class ClockMutableConfig {
        @Bean
        @Primary
        MutableClock relojMutable() {
            return new MutableClock();
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private MutableClock clock;
    @MockitoSpyBean
    private AuthenticationManager authenticationManager;

    private MockMvc mvc;
    private String email;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = "ana-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        users.save(new User(email, "Ana", encoder.encode(PASSWORD), Role.USER, Instant.EPOCH));
    }

    private MvcResult login(String mail, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(mail, password)))
                .andReturn();
    }

    private int status(String mail, String password, String ip) throws Exception {
        return login(mail, password, ip).getResponse().getStatus();
    }

    private void fail(String mail, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            assertThat(status(mail, "incorrecta", ip)).isEqualTo(401);
        }
    }

    @Test
    void tras_N_fallos_el_siguiente_da_429_aunque_la_clave_sea_correcta_con_retry_after() throws Exception {
        fail(email, IP, MAX);

        MvcResult blocked = login(email, PASSWORD, IP);

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isEqualTo("600");
        assertThat(blocked.getResponse().getContentType()).contains("application/problem+json");
        assertThat(blocked.getResponse().getContentAsString()).contains("\"status\":429");
    }

    @Test
    void retry_after_se_redondea_hacia_arriba_y_vale_al_menos_1() throws Exception {
        fail(email, IP, MAX);

        clock.advance(Duration.ofMillis(100_500));
        assertThat(login(email, PASSWORD, IP).getResponse().getHeader("Retry-After")).isEqualTo("500");

        clock.advance(Duration.ofMillis(499_400));
        assertThat(login(email, PASSWORD, IP).getResponse().getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void el_429_es_identico_exista_o_no_el_email() throws Exception {
        String ghost = "fantasma-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        fail(email, IP, MAX);
        fail(ghost, IP, MAX);

        MvcResult real = login(email, PASSWORD, IP);
        MvcResult inexistente = login(ghost, PASSWORD, IP);

        assertThat(inexistente.getResponse().getStatus()).isEqualTo(429);
        assertThat(inexistente.getResponse().getContentAsString())
                .isEqualTo(real.getResponse().getContentAsString());
        assertThat(inexistente.getResponse().getHeader("Retry-After"))
                .isEqualTo(real.getResponse().getHeader("Retry-After"));
        assertThat(real.getResponse().getContentAsString()).doesNotContain(email).doesNotContain(ghost);
    }

    @Test
    void pasada_la_ventana_se_puede_volver_a_intentar_y_un_login_correcto_funciona() throws Exception {
        fail(email, IP, MAX);
        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);

        clock.advance(Duration.ofMinutes(10));

        assertThat(status(email, PASSWORD, IP)).isEqualTo(200);
    }

    @Test
    void pasada_la_ventana_la_serie_empieza_de_cero() throws Exception {
        fail(email, IP, MAX);
        clock.advance(Duration.ofMinutes(10));

        fail(email, IP, MAX);

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
    }

    @Test
    void la_ventana_cuenta_desde_el_primer_fallo_y_no_se_alarga_con_los_siguientes() throws Exception {
        fail(email, IP, 1);
        clock.advance(Duration.ofMinutes(9));
        fail(email, IP, MAX - 1);
        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1));

        assertThat(status(email, PASSWORD, IP)).isEqualTo(200);
    }

    @Test
    void el_intento_numero_N_con_clave_correcta_entra_y_reinicia_la_serie() throws Exception {
        fail(email, IP, MAX - 1);
        assertThat(status(email, PASSWORD, IP)).isEqualTo(200);

        fail(email, IP, MAX);

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
    }

    @Test
    void los_400_por_cuerpo_no_valido_no_cuentan() throws Exception {
        for (int i = 0; i < MAX + 3; i++) {
            assertThat(status("", "x", IP)).isEqualTo(400);
            assertThat(status(email, "", IP)).isEqualTo(400);
            assertThat(status("no-es-un-email", "x", IP)).isEqualTo(400);
        }
        assertThat(status(email, PASSWORD, IP)).isEqualTo(200);
    }

    @Test
    void el_json_malformado_no_cuenta() throws Exception {
        for (int i = 0; i < MAX + 2; i++) {
            MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(IP);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{no es json")).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
        }
        fail(email, IP, MAX);
        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
    }

    @Test
    void mayusculas_en_el_email_cuentan_como_el_mismo() throws Exception {
        assertThat(status(email.toUpperCase(Locale.ROOT), "incorrecta", IP)).isEqualTo(401);
        assertThat(status(email, "incorrecta", IP)).isEqualTo(401);
        assertThat(status(email.toUpperCase(Locale.ROOT), "incorrecta", IP)).isEqualTo(401);

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
        assertThat(status(email.toUpperCase(Locale.ROOT), PASSWORD, IP)).isEqualTo(429);
    }

    @Test
    void un_email_con_espacios_se_rechaza_con_400_antes_de_contar() throws Exception {
        // La validación @Email no admite espacios; la normalización de espacios la cubre LoginAttemptServiceTest.
        for (int i = 0; i < MAX + 2; i++) {
            assertThat(status("  " + email + "  ", "incorrecta", IP)).isEqualTo(400);
        }
        assertThat(status(email, PASSWORD, IP)).isEqualTo(200);
    }

    @Test
    void otra_ip_con_el_mismo_email_no_esta_bloqueada() throws Exception {
        fail(email, IP, MAX);

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
        assertThat(status(email, PASSWORD, "198.51.100.11")).isEqualTo(200);
    }

    @Test
    void otro_email_desde_la_misma_ip_no_esta_bloqueado() throws Exception {
        String otro = "bea-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        users.save(new User(otro, "Bea", encoder.encode(PASSWORD), Role.USER, Instant.EPOCH));
        fail(email, IP, MAX);

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
        assertThat(status(otro, PASSWORD, IP)).isEqualTo(200);
    }

    @Test
    void un_usuario_desactivado_cuenta_como_fallo_y_responde_igual_que_uno_inexistente() throws Exception {
        User inactivo = users.findByEmail(email).orElseThrow();
        inactivo.update("Ana", Role.USER, false);
        users.save(inactivo);

        for (int i = 0; i < MAX; i++) {
            assertThat(status(email, PASSWORD, IP)).isEqualTo(401);
        }

        assertThat(status(email, PASSWORD, IP)).isEqualTo(429);
    }

    @Test
    void con_intentos_simultaneos_nunca_se_comprueban_mas_de_N_credenciales() throws Exception {
        int total = MAX + 5;
        clearInvocations(authenticationManager);
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < total; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return status(email, "incorrecta", IP);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int unauthorized = 0;
            int tooMany = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                if (code == 401) {
                    unauthorized++;
                } else if (code == 429) {
                    tooMany++;
                }
            }
            assertThat(unauthorized).isEqualTo(MAX);
            assertThat(tooMany).isEqualTo(total - MAX);
        } finally {
            pool.shutdownNow();
        }
        verify(authenticationManager, atMost(MAX)).authenticate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void el_log_del_429_no_contiene_ni_el_email_ni_la_ip(CapturedOutput output) throws Exception {
        String ip = "203.0.113.77";
        fail(email, ip, MAX);

        assertThat(status(email, PASSWORD, ip)).isEqualTo(429);

        assertThat(output.getAll()).contains("bloqueada por demasiados fallos");
        assertThat(output.getAll()).doesNotContain(email).doesNotContain(email.toUpperCase(Locale.ROOT)).doesNotContain(ip);
    }
}
