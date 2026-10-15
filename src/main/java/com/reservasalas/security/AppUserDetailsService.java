package com.reservasalas.security;

import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga los usuarios de la base de datos para el login, buscándolos por email sin distinguir
 * mayúsculas. Los usuarios desactivados se tratan como inexistentes: así el login falla con el
 * mismo mensaje genérico y, como el proveedor de autenticación compara igualmente la contraseña con
 * un hash ficticio, tampoco el tiempo de respuesta revela que la cuenta existe.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Crea el servicio.
     *
     * @param userRepository repositorio de usuarios
     */
    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Busca el usuario activo con ese email.
     *
     * @param email email introducido en el login
     * @return el usuario con el hash de su contraseña
     * @throws UsernameNotFoundException si no hay ningún usuario activo con ese email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailAndActiveTrue(User.normalizeEmail(email))
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
