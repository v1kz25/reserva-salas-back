package com.reservasalas.repository;

import com.reservasalas.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de usuarios.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su email.
     *
     * @param email email ya normalizado
     * @return el usuario, si existe
     */
    Optional<User> findByEmail(String email);

    /**
     * Comprueba si ya hay un usuario con ese email.
     *
     * @param email email ya normalizado
     * @return {@code true} si existe
     */
    boolean existsByEmail(String email);
}
