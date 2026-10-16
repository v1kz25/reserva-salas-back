package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

/**
 * Usuario de la aplicación. El email es único y se guarda normalizado (sin espacios alrededor y en
 * minúsculas); la contraseña se guarda solo como hash BCrypt. Un usuario desactivado no puede
 * iniciar sesión.
 *
 * <p>La versión de sesión ({@code SESSION_VERSION}) se incrementa cada vez que cambian el rol, el
 * estado o la contraseña. Cada sesión guarda la versión con la que se abrió, y una sesión cuya
 * versión ya no coincide se cierra en su siguiente petición. Esa columna no la escribe Hibernate al
 * modificar la entidad: se incrementa con una sentencia SQL atómica
 * ({@code UserRepository#incrementSessionVersion}), para que dos cambios simultáneos no pierdan
 * ningún incremento.
 *
 * <p>La columna {@code VERSION} es la del bloqueo optimista: dos modificaciones simultáneas del
 * mismo usuario no se pisan, la segunda falla.
 */
@Entity
@Table(name = "USERS", uniqueConstraints = @UniqueConstraint(name = User.UNIQUE_EMAIL_CONSTRAINT, columnNames = "EMAIL"))
public class User {

    /** Longitud máxima del email. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** Longitud máxima del nombre. */
    public static final int MAX_NAME_LENGTH = 100;

    /** Nombre de la restricción única sobre el email normalizado. */
    public static final String UNIQUE_EMAIL_CONSTRAINT = "UK_USERS_EMAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EMAIL", nullable = false, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "NAME", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 10)
    private Role role;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "SESSION_VERSION", nullable = false, updatable = false)
    private long sessionVersion;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

    protected User() {
        // Requerido por JPA
    }

    /**
     * Crea un usuario nuevo y activo, aún sin persistir. El email se normaliza y el nombre se
     * limpia con {@link #cleanName(String)}.
     *
     * @param email        email con el que inicia sesión
     * @param name         nombre visible del usuario
     * @param passwordHash hash BCrypt de la contraseña (nunca la contraseña en claro)
     * @param role         rol del usuario
     * @param createdAt    instante de creación
     */
    public User(String email, String name, String passwordHash, Role role, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.name = cleanName(name);
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.createdAt = createdAt;
        this.sessionVersion = 0;
    }

    /**
     * Normaliza un email para guardarlo o buscarlo: quita los espacios de alrededor y lo pasa a
     * minúsculas.
     *
     * @param email email tal y como lo escribe el usuario
     * @return el email normalizado, o {@code null} si era nulo
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Nombre tal y como se guarda: en forma Unicode NFC y sin espacios en los extremos.
     *
     * @param name nombre visible del usuario
     * @return el nombre limpio, o {@code null} si era nulo
     */
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        return Normalizer.normalize(name, Normalizer.Form.NFC).strip();
    }

    /**
     * Cambia el nombre, el rol y el estado. No toca la versión de sesión: si este método devuelve
     * {@code true}, quien lo llama debe incrementarla en la base de datos para cerrar las sesiones
     * abiertas del usuario.
     *
     * @param newName   nuevo nombre visible; se limpia con {@link #cleanName(String)}
     * @param newRole   nuevo rol
     * @param newActive {@code true} si el usuario queda activo
     * @return {@code true} si han cambiado el rol o el estado
     */
    public boolean update(String newName, Role newRole, boolean newActive) {
        boolean sessionsMustClose = newRole != role || newActive != active;
        this.name = cleanName(newName);
        this.role = newRole;
        this.active = newActive;
        return sessionsMustClose;
    }

    /**
     * Sustituye el hash de la contraseña. No toca la versión de sesión: quien lo llama debe
     * incrementarla en la base de datos para cerrar las sesiones abiertas del usuario.
     *
     * @param newPasswordHash hash BCrypt de la nueva contraseña
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Indica si es un administrador activo.
     *
     * @return {@code true} si tiene el rol {@link Role#ADMIN} y está activo
     */
    public boolean isActiveAdmin() {
        return active && role == Role.ADMIN;
    }

    /**
     * Identificador del usuario.
     *
     * @return el identificador, o {@code null} si aún no se ha persistido
     */
    public Long getId() {
        return id;
    }

    /**
     * Email normalizado del usuario.
     *
     * @return el email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Nombre visible del usuario.
     *
     * @return el nombre
     */
    public String getName() {
        return name;
    }

    /**
     * Hash BCrypt de la contraseña.
     *
     * @return el hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Rol del usuario.
     *
     * @return el rol
     */
    public Role getRole() {
        return role;
    }

    /**
     * Indica si el usuario está activo y puede iniciar sesión.
     *
     * @return {@code true} si está activo
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Instante en el que se creó el usuario.
     *
     * @return el instante de creación
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Versión de sesión con la que se cargó la entidad: las sesiones abiertas con otra versión ya no
     * son válidas. No refleja los incrementos hechos en la base de datos después de cargarla.
     *
     * @return la versión de sesión
     */
    public long getSessionVersion() {
        return sessionVersion;
    }
}
