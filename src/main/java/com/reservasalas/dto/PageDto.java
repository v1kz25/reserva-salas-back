package com.reservasalas.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados tal y como se expone en la API (p. ej. {@code PaginaReservas} en el
 * contrato). Se usa en lugar de serializar {@link Page} directamente para que el JSON sea estable y
 * solo tenga los campos del contrato.
 *
 * @param content       elementos de la página, vacía si la página está fuera de rango
 * @param totalElements número total de elementos que cumplen el filtro
 * @param totalPages    número total de páginas
 * @param number        página actual, empezando en 0
 * @param size          tamaño de página solicitado
 * @param <T>           tipo de los elementos
 */
public record PageDto<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size) {

    /**
     * Tamaño de página por defecto, como texto para poder usarlo en {@code @RequestParam(defaultValue)}.
     */
    public static final String DEFAULT_SIZE = "20";

    /**
     * Tamaño de página máximo admitido.
     */
    public static final int MAX_SIZE = 100;

    /**
     * Copia la lista de elementos para que la página sea inmutable.
     *
     * @param content       elementos de la página
     * @param totalElements número total de elementos
     * @param totalPages    número total de páginas
     * @param number        página actual
     * @param size          tamaño de página
     */
    public PageDto {
        content = List.copyOf(content);
    }

    /**
     * Construye la página a partir de una página de Spring Data, transformando cada elemento.
     *
     * @param page   página de Spring Data
     * @param mapper transformación de cada elemento
     * @param <S>    tipo de los elementos de origen
     * @param <T>    tipo de los elementos de la página resultante
     * @return página equivalente con los elementos transformados
     */
    public static <S, T> PageDto<T> from(Page<S> page, Function<? super S, ? extends T> mapper) {
        List<T> content = page.getContent().stream()
                .<T>map(mapper)
                .toList();
        return new PageDto<>(content, page.getTotalElements(), page.getTotalPages(), page.getNumber(),
                page.getSize());
    }
}
