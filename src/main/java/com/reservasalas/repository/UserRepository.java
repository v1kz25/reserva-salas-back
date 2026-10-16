package com.reservasalas.repository;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Lista los usuarios activos con ese rol y bloquea sus filas hasta el final de la transacción,
     * para serializar los cambios que podrían dejar la aplicación sin administradores activos.
     *
     * @param role rol buscado
     * @return los usuarios activos con ese rol, vacía si no hay ninguno
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findAllByRoleAndActiveTrueOrderByIdAsc(Role role);

    /**
     * Incrementa de forma atómica la versión de sesión del usuario, lo que cierra sus sesiones
     * abiertas. Antes vuelca los cambios pendientes de la transacción.
     *
     * @param id identificador del usuario
     * @return número de filas modificadas (1 si el usuario existe)
     */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.sessionVersion = u.sessionVersion + 1 where u.id = :id")
    int incrementSessionVersion(@Param("id") Long id);
}
