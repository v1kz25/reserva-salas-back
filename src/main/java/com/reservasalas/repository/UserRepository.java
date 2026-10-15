package com.reservasalas.repository;

import com.reservasalas.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Busca un usuario activo por su email.
     *
     * @param email email ya normalizado
     * @return el usuario, si existe y está activo
     */
    Optional<User> findByEmailAndActiveTrue(String email);

    /**
     * Comprueba si ya hay un usuario con ese email.
     *
     * @param email email ya normalizado
     * @return {@code true} si existe
     */
    boolean existsByEmail(String email);

    /**
     * Lista todos los usuarios ordenados por nombre y, a igualdad de nombre, por identificador.
     *
     * @return los usuarios, vacía si no hay ninguno
     */
    List<User> findAllByOrderByNameAscIdAsc();

    /**
     * Obtiene solo el estado del usuario que decide si sus sesiones siguen siendo válidas, sin
     * cargar la entidad completa.
     *
     * @param id identificador del usuario
     * @return el estado, si el usuario existe
     */
    @Query("""
            select new com.reservasalas.repository.UserSessionState(u.active, u.sessionVersion)
            from User u
            where u.id = :id""")
    Optional<UserSessionState> findSessionStateById(@Param("id") Long id);
}
