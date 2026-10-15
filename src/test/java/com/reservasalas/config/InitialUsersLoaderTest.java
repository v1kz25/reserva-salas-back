package com.reservasalas.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Creación de las cuentas iniciales a partir de las variables de entorno (propiedades
 * {@code app.initial-users.*}).
 */
class InitialUsersLoaderTest {

    private static final String ADMIN_PASSWORD = "clave-admin-prueba";
    private static final String TOO_LONG = "0123456789012345678901234567890123456789012345678901234567890123456789abc";
    private static final String DEMO_PASSWORD = "clave-demo-prueba";

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:iu-admin-${random.uuid}",
            "app.initial-users.admin.email=  Root@Example.COM ",
            "app.initial-users.admin.password=" + ADMIN_PASSWORD,
            "app.initial-users.demo.email=demo@example.com",
            "app.initial-users.demo.password=" + DEMO_PASSWORD})
    @ExtendWith(OutputCaptureExtension.class)
    class ConAdminSinPerfilDev {
        @Autowired
        UserRepository users;
        @Autowired
        InitialUsersLoader loader;
        @Autowired
        PasswordEncoder encoder;

        @Test
        void creaElAdminNormalizadoConContrasenaBcrypt() {
            User admin = users.findByEmail("root@example.com").orElseThrow();

            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(admin.getName()).isEqualTo("Administrador");
            assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD).startsWith("$2");
            assertThat(encoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
        }

        @Test
        void esIdempotenteYNoSobrescribeUnaCuentaExistente(CapturedOutput output) {
            String hash = users.findByEmail("root@example.com").orElseThrow().getPasswordHash();
            long total = users.count();

            loader.run(null);
            loader.run(null);

            assertThat(users.count()).isEqualTo(total);
            assertThat(users.findByEmail("root@example.com").orElseThrow().getPasswordHash()).isEqualTo(hash);
            assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD).doesNotContain(DEMO_PASSWORD);
        }

        @Test
        void sinPerfilDevNoCreaElUsuarioDemo() {
            assertThat(users.findByEmail("demo@example.com")).isEmpty();
            assertThat(users.count()).isEqualTo(1);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:iu-dev-${random.uuid}",
            "app.initial-users.admin.email=root@example.com",
            "app.initial-users.admin.password=" + ADMIN_PASSWORD,
            "app.initial-users.demo.email=Demo@Example.com",
            "app.initial-users.demo.password=" + DEMO_PASSWORD})
    @ActiveProfiles("dev")
    @ExtendWith(OutputCaptureExtension.class)
    class ConPerfilDev {
        @Autowired
        UserRepository users;
        @Autowired
        InitialUsersLoader loader;
        @Autowired
        PasswordEncoder encoder;

        @Test
        void creaElUsuarioDemoConRolUserYContrasenaBcrypt() {
            User demo = users.findByEmail("demo@example.com").orElseThrow();

            assertThat(demo.getRole()).isEqualTo(Role.USER);
            assertThat(encoder.matches(DEMO_PASSWORD, demo.getPasswordHash())).isTrue();
            assertThat(demo.getPasswordHash()).doesNotContain(DEMO_PASSWORD);
            assertThat(users.findByEmail("root@example.com").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        }

        @Test
        void esIdempotenteYElLogNoIncluyeContrasenas(CapturedOutput output) {
            long total = users.count();

            loader.run(null);

            assertThat(users.count()).isEqualTo(total).isEqualTo(2);
            assertThat(output.getAll()).doesNotContain(ADMIN_PASSWORD).doesNotContain(DEMO_PASSWORD);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:iu-nopass-${random.uuid}",
            "app.initial-users.admin.email=root@example.com",
            "app.initial-users.admin.password="})
    @ExtendWith(OutputCaptureExtension.class)
    class SinContrasena {
        @Autowired
        UserRepository users;
        @Autowired
        InitialUsersLoader loader;

        @Test
        void noCreaElAdminYAvisaEnElLog(CapturedOutput output) {
            loader.run(null);

            assertThat(users.count()).isZero();
            assertThat(output.getAll()).contains("ADMIN_PASSWORD");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:iu-long-${random.uuid}",
            "app.initial-users.admin.email=root@example.com",
            "app.initial-users.admin.password=" + "0123456789012345678901234567890123456789012345678901234567890123456789abc"})
    @ExtendWith(OutputCaptureExtension.class)
    class ContrasenaDeMasDe72Bytes {
        @Autowired
        UserRepository users;
        @Autowired
        InitialUsersLoader loader;

        @Test
        void noCreaElAdminAvisaYNoEscribeLaContrasenaEnElLog(CapturedOutput output) {
            loader.run(null);

            assertThat(users.count()).isZero();
            assertThat(output.getAll()).contains("72").doesNotContain(TOO_LONG);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:iu-72-${random.uuid}",
            "app.initial-users.admin.email=root@example.com",
            "app.initial-users.admin.password=" + "0123456789012345678901234567890123456789012345678901234567890123456789ab"})
    class ContrasenaDeExactamente72Bytes {
        @Autowired
        UserRepository users;

        @Test
        void siSeCrea() {
            assertThat(users.findByEmail("root@example.com")).isPresent();
        }
    }
}
