package com.reservasalas.controller;

import com.reservasalas.dto.UserSummaryDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.UserSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buscador de usuarios por nombre, para cualquier usuario autenticado (el resto de
 * {@code /api/usuarios} es solo para administradores, ver {@link UserController}).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UserSearchController {

    private final UserSearchService userSearchService;

    /**
     * Crea el controlador.
     *
     * @param userSearchService servicio del buscador de usuarios
     */
    public UserSearchController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    /**
     * Busca usuarios activos por nombre, sin distinguir mayúsculas ni acentos y sin incluir al
     * usuario de la sesión.
     *
     * @param query       texto de búsqueda, de 2 a 100 caracteres una vez recortado
     * @param currentUser usuario de la sesión
     * @return como mucho 20 usuarios, ordenados por nombre
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<UserSummaryDto>> search(@RequestParam("q") String query,
                                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(userSearchService.search(query, currentUser.getId()));
    }
}
