package com.reservasalas.service;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de cuentas de usuario con la contraseña guardada en BCrypt.
 */
@Service
public class UserAccountService {

    /** Límite de BCrypt: las contraseñas más largas no se pueden codificar. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea el servicio.
     *
     * @param userRepository  repositorio de usuarios
     * @param passwordEncoder codificador de contraseñas
     */
    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea un usuario si no existe ninguno con ese email; si ya existe, no lo modifica.
     *
     * @param email       email del usuario; se normaliza
     * @param rawPassword contraseña en claro, de 72 bytes como máximo en UTF-8
     * @param name        nombre visible
     * @param role        rol del usuario
     * @return {@code true} si se ha creado, {@code false} si ya existía
     * @throws IllegalArgumentException si la contraseña supera {@value #MAX_PASSWORD_BYTES} bytes
     */
    @Transactional
    public boolean createIfAbsent(String email, String rawPassword, String name, Role role) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "La contraseña no puede superar los " + MAX_PASSWORD_BYTES + " bytes en UTF-8");
        }
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            return false;
        }
        userRepository.save(new User(normalized, name, passwordEncoder.encode(rawPassword), role));
        return true;
    }
}
