package com.reservasalas.config;

import com.reservasalas.config.InitialUsersProperties.Account;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.service.UserAccountService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Crea al arrancar el administrador inicial y, con el perfil {@code dev}, el usuario de prueba,
 * si no existen ya. Los datos salen de variables de entorno; nunca se escriben en el log.
 */
@Component
@EnableConfigurationProperties(InitialUsersProperties.class)
public class InitialUsersLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialUsersLoader.class);
    private static final String DEFAULT_ADMIN_NAME = "Administrador";
    private static final String DEFAULT_DEMO_NAME = "Usuario de prueba";
    private static final String DEV_PROFILE = "dev";

    private final InitialUsersProperties properties;
    private final UserAccountService userAccountService;
    private final Environment environment;

    /**
     * Crea el cargador.
     *
     * @param properties         cuentas iniciales configuradas
     * @param userAccountService servicio de alta de usuarios
     * @param environment        entorno, para saber si el perfil {@code dev} está activo
     */
    public InitialUsersLoader(InitialUsersProperties properties, UserAccountService userAccountService,
                              Environment environment) {
        this.properties = properties;
        this.userAccountService = userAccountService;
        this.environment = environment;
    }

    /**
     * Crea las cuentas iniciales que falten.
     *
     * @param args argumentos de arranque (no se usan)
     */
    @Override
    public void run(ApplicationArguments args) {
        createAdmin();
        if (environment.matchesProfiles(DEV_PROFILE)) {
            createDemoUser();
        }
    }

    private void createAdmin() {
        Account admin = properties.admin();
        if (isBlank(admin.password())) {
            LOG.warn("No se ha creado el administrador inicial: falta la variable ADMIN_PASSWORD");
            return;
        }
        if (isBlank(admin.email())) {
            LOG.warn("No se ha creado el administrador inicial: falta la variable ADMIN_EMAIL");
            return;
        }
        create(admin, Role.ADMIN, DEFAULT_ADMIN_NAME, "administrador inicial");
    }

    private void createDemoUser() {
        Account demo = properties.demo();
        if (isBlank(demo.email()) || isBlank(demo.password())) {
            LOG.info("No se ha creado el usuario de prueba: faltan DEMO_EMAIL o DEMO_PASSWORD");
            return;
        }
        create(demo, Role.USER, DEFAULT_DEMO_NAME, "usuario de prueba");
    }

    /**
     * Comprueba los límites de la cuenta (que no se validan en ningún otro sitio porque no llegan
     * por la API) y la crea si no existe.
     */
    private void create(Account account, Role role, String defaultName, String label) {
        String name = isBlank(account.name()) ? defaultName : account.name().strip();
        if (account.password().getBytes(StandardCharsets.UTF_8).length > UserAccountService.MAX_PASSWORD_BYTES) {
            LOG.warn("No se ha creado el {}: la contraseña supera los {} bytes", label,
                    UserAccountService.MAX_PASSWORD_BYTES);
            return;
        }
        if (User.normalizeEmail(account.email()).length() > User.MAX_EMAIL_LENGTH
                || name.length() > User.MAX_NAME_LENGTH) {
            LOG.warn("No se ha creado el {}: el email o el nombre son demasiado largos", label);
            return;
        }
        if (userAccountService.createIfAbsent(account.email(), account.password(), name, role)) {
            LOG.info("Creado el {}", label);
        } else {
            LOG.info("El {} ya existe; no se modifica", label);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
