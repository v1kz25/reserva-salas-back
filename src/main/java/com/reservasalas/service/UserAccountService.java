package com.reservasalas.service;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de cuentas de usuario con la contraseña guardada en BCrypt.
 */
@Service
public class UserAccountService {

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
     */
    @Transactional
    public boolean createIfAbsent(String email, String rawPassword, String name, Role role) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            return false;
        }
        userRepository.save(new User(normalized, name, passwordEncoder.encode(rawPassword), role));
        return true;
    }
}
