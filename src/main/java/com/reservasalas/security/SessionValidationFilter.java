package com.reservasalas.security;

import com.reservasalas.repository.UserRepository;
import com.reservasalas.repository.UserSessionState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cierra al instante las sesiones que ya no son válidas: en cada petición con sesión comprueba en
 * la base de datos que el usuario sigue existiendo, está activo y conserva la versión de sesión con
 * la que inició sesión. Si no, invalida la sesión HTTP y continúa como petición anónima, de modo
 * que las rutas que exigen sesión responden {@code 401}.
 *
 * <p>La versión de sesión cambia al desactivar al usuario, al cambiarle el rol o la contraseña
 * (ver {@link com.reservasalas.entity.User}). Al guardarse en la base de datos, la comprobación no
 * depende de un registro de sesiones en memoria.
 */
public final class SessionValidationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionValidationFilter.class);

    private final UserRepository userRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * Crea el filtro.
     *
     * @param userRepository repositorio de usuarios, para leer su estado actual
     */
    public SessionValidationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && !isStillValid(user)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            contextHolderStrategy.clearContext();
            LOG.info("Sesión cerrada del usuario {}: su estado, rol o contraseña han cambiado", user.getId());
        }
        filterChain.doFilter(request, response);
    }

    private boolean isStillValid(AuthenticatedUser user) {
        return userRepository.findSessionStateById(user.getId())
                .filter(UserSessionState::active)
                .map(state -> state.sessionVersion() == user.getSessionVersion())
                .orElse(false);
    }
}
