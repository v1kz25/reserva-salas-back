package com.reservasalas.service;

import com.reservasalas.dto.UserSummaryDto;
import com.reservasalas.entity.User;
import com.reservasalas.exception.InvalidSearchQueryException;
import com.reservasalas.repository.UserRepository;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buscador de usuarios por nombre, para cualquier usuario autenticado. Compara sin distinguir
 * mayúsculas ni acentos, excluye al propio usuario y a los desactivados y nunca expone el email.
 */
@Service
public class UserSearchService {

    /** Longitud mínima del texto de búsqueda, una vez recortado. */
    public static final int MIN_QUERY_LENGTH = 2;

    /** Longitud máxima del texto de búsqueda, una vez recortado. */
    public static final int MAX_QUERY_LENGTH = 100;

    /** Número máximo de resultados. */
    public static final int MAX_RESULTS = 20;

    private static final Pattern LIKE_SPECIAL_CHARS = Pattern.compile("[\\\\%_]");

    private final UserRepository userRepository;

    /**
     * Crea el servicio.
     *
     * @param userRepository repositorio de usuarios
     */
    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Busca usuarios activos cuyo nombre contiene el texto indicado, sin distinguir mayúsculas ni
     * acentos. Devuelve como mucho {@value #MAX_RESULTS} resultados ordenados por nombre.
     *
     * @param query         texto de búsqueda; se recortan los espacios de los extremos
     * @param currentUserId identificador del usuario de la sesión, que se excluye
     * @return los usuarios encontrados, vacía si no hay ninguno
     * @throws InvalidSearchQueryException si el texto recortado tiene menos de
     *                                     {@value #MIN_QUERY_LENGTH} o más de
     *                                     {@value #MAX_QUERY_LENGTH} caracteres
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> search(String query, Long currentUserId) {
        String trimmed = query.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < MIN_QUERY_LENGTH || length > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException(MIN_QUERY_LENGTH, MAX_QUERY_LENGTH);
        }
        String key = User.searchKey(trimmed);
        // Un texto formado solo por marcas diacríticas se queda vacío y encontraría a todos.
        if (key.isEmpty()) {
            return List.of();
        }
        String pattern = "%" + escapeLike(key) + "%";
        return userRepository.searchActiveByName(currentUserId, pattern, PageRequest.of(0, MAX_RESULTS));
    }

    /**
     * Escapa con {@code \} los comodines de {@code LIKE} ({@code %} y {@code _}) y el propio carácter
     * de escape, para que el texto se busque literalmente.
     */
    private static String escapeLike(String text) {
        return LIKE_SPECIAL_CHARS.matcher(text)
                .replaceAll(match -> Matcher.quoteReplacement("\\" + match.group()));
    }
}
