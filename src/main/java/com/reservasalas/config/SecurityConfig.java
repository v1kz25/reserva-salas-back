package com.reservasalas.config;

import com.reservasalas.entity.Role;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.ProblemDetailSecurityHandler;
import com.reservasalas.security.SessionValidationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Seguridad de la API: sesión HTTP en cookie, protección CSRF con cookie {@code XSRF-TOKEN} y
 * autorización por rol.
 *
 * <p>Todo {@code /api/**} exige sesión salvo {@code GET /api/auth/csrf} y
 * {@code POST /api/auth/login}. Crear, modificar y eliminar salas, gestionar usuarios y cambiar la
 * zona horaria es solo para administradores; el buscador {@code GET /api/usuarios/buscar} queda
 * fuera de esa regla porque es para cualquier usuario autenticado. El resto de rutas (fuera de la
 * API) no requieren sesión y acaban en el {@code 404} genérico.
 *
 * <p>En cada petición, {@link SessionValidationFilter} cierra la sesión si el usuario se ha
 * desactivado o han cambiado su rol o su contraseña desde que la abrió.
 */
@Configuration
public class SecurityConfig {

    private static final String ROOMS = "/api/salas/**";
    private static final String TIME_ZONE = "/api/config/zona-horaria";
    private static final String USERS = "/api/usuarios";
    private static final String USERS_SUBPATHS = "/api/usuarios/**";
    private static final String USER_SEARCH = "/api/usuarios/buscar";
    private static final String SAME_SITE_STRICT = "Strict";

    /**
     * Cadena de filtros de seguridad principal.
     *
     * @param http                      constructor de la cadena
     * @param csrfTokenRepository       repositorio del token CSRF en cookie
     * @param securityContextRepository repositorio del contexto de seguridad en la sesión
     * @param exceptionResolver         resolutor de excepciones de Spring MVC, para responder
     *                                  {@code 401} y {@code 403} como {@code ProblemDetail}
     * @param corsConfigurationSource   configuración CORS; solo existe con el perfil {@code dev}
     * @param userRepository            repositorio de usuarios, para comprobar en cada petición que
     *                                  la sesión sigue siendo válida
     * @return la cadena de filtros
     * @throws Exception si la configuración no es válida
     */
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            ObjectProvider<CorsConfigurationSource> corsConfigurationSource,
            UserRepository userRepository) throws Exception {
        var problemHandler = new ProblemDetailSecurityHandler(exceptionResolver);
        http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterAfter(new SessionValidationFilter(userRepository), SecurityContextHolderFilter.class)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, ROOMS).hasAuthority(Role.ADMIN.authority())
                        .requestMatchers(HttpMethod.PUT, ROOMS).hasAuthority(Role.ADMIN.authority())
                        .requestMatchers(HttpMethod.DELETE, ROOMS).hasAuthority(Role.ADMIN.authority())
                        .requestMatchers(HttpMethod.PUT, TIME_ZONE).hasAuthority(Role.ADMIN.authority())
                        .requestMatchers(HttpMethod.GET, USER_SEARCH).authenticated()
                        .requestMatchers(USERS, USERS_SUBPATHS).hasAuthority(Role.ADMIN.authority())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll());

        CorsConfigurationSource cors = corsConfigurationSource.getIfAvailable();
        if (cors != null) {
            http.cors(c -> c.configurationSource(cors));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }

    /**
     * Repositorio del token CSRF: cookie {@code XSRF-TOKEN} legible desde JavaScript (el cliente la
     * repite en la cabecera {@code X-XSRF-TOKEN}) y con {@code SameSite=Strict}.
     *
     * @param secureCookies si la cookie se marca como {@code Secure} (solo HTTPS)
     * @return el repositorio
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository(@Value("${app.cookie-secure:false}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite(SAME_SITE_STRICT).secure(secureCookies));
        return repository;
    }

    /**
     * Repositorio del contexto de seguridad: se guarda en la sesión HTTP (y en la petición en
     * curso), de forma que el login hecho en el controlador persista entre peticiones.
     *
     * @return el repositorio
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }
}
