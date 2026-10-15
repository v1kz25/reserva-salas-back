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
import java.util.Locale;

/**
 * Usuario que puede iniciar sesión en la aplicación. El email es único y se guarda normalizado
 * (sin espacios alrededor y en minúsculas); la contraseña se guarda solo como hash BCrypt.
 */
@Entity
@Table(name = "USERS", uniqueConstraints = @UniqueConstraint(name = "UK_USERS_EMAIL", columnNames = "EMAIL"))
public class User {

    /** Longitud máxima del email. */
    public static final int MAX_EMAIL_LENGTH = 254;

    /** Longitud máxima del nombre. */
    public static final int MAX_NAME_LENGTH = 100;

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

    protected User() {
        // Requerido por JPA
    }

    /**
     * Crea un usuario nuevo, aún sin persistir. El email se normaliza.
     *
     * @param email        email con el que inicia sesión
     * @param name         nombre visible del usuario
     * @param passwordHash hash BCrypt de la contraseña (nunca la contraseña en claro)
     * @param role         rol del usuario
     */
    public User(String email, String name, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = role;
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
}
