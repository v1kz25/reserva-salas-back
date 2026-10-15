package com.reservasalas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cuentas que se crean al arrancar si no existen, leídas de variables de entorno.
 *
 * @param admin administrador inicial ({@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD}, {@code ADMIN_NOMBRE})
 * @param demo  usuario de prueba, solo con el perfil {@code dev} ({@code DEMO_EMAIL},
 *              {@code DEMO_PASSWORD}, {@code DEMO_NOMBRE})
 */
@ConfigurationProperties(prefix = "app.initial-users")
public record InitialUsersProperties(Account admin, Account demo) {

    /**
     * Normaliza las cuentas ausentes a cuentas vacías.
     */
    public InitialUsersProperties {
        admin = admin == null ? Account.EMPTY : admin;
        demo = demo == null ? Account.EMPTY : demo;
    }

    /**
     * Datos de una cuenta inicial. Cualquiera de ellos puede venir vacío.
     *
     * @param email    email de la cuenta
     * @param password contraseña en claro; solo se usa para calcular su hash
     * @param name     nombre visible; si viene vacío se usa uno por defecto
     */
    public record Account(String email, String password, String name) {

        /** Cuenta sin datos. */
        public static final Account EMPTY = new Account(null, null, null);

        @Override
        public String toString() {
            return "Account[email=***, password=***, name=" + name + "]";
        }
    }
}
