package com.reservasalas.security;

import com.reservasalas.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Actualiza la sesión en curso cuando un administrador se modifica a sí mismo: guarda en ella los
 * datos y la versión de sesión actuales, de modo que su propia sesión sigue abierta (y
 * {@code /api/auth/me} refleja el cambio) mientras que el resto de sus sesiones se cierran.
 */
@Component
public class CurrentSessionUpdater {

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * Crea el componente.
     *
     * @param userRepository            repositorio de usuarios
     * @param securityContextRepository repositorio donde se guarda la sesión autenticada
     */
    public CurrentSessionUpdater(UserRepository userRepository,
                                 SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Si el usuario modificado es el de la sesión, vuelve a cargarlo y lo guarda en la sesión. Si
     * es otro usuario, no hace nada.
     *
     * @param modifiedUserId identificador del usuario modificado
     * @param current        usuario de la sesión en curso
     * @param request        petición en curso
     * @param response       respuesta en curso
     */
    public void refreshIfCurrent(Long modifiedUserId, AuthenticatedUser current,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (!modifiedUserId.equals(current.getId())) {
            return;
        }
        userRepository.findById(modifiedUserId).ifPresent(user -> {
            AuthenticatedUser refreshed = AuthenticatedUser.from(user);
            refreshed.eraseCredentials();
            SecurityContext context = contextHolderStrategy.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    refreshed, null, refreshed.getAuthorities()));
            contextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        });
    }
}
