package com.reservasalas.exception;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Detección común de violaciones de una restricción única a partir de la excepción que devuelve la
 * capa de persistencia, para que los servicios la traduzcan a su excepción de dominio ({@code 409}).
 */
public final class UniqueConstraintViolations {

    /** SQLSTATE estándar de violación de unicidad. */
    static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private UniqueConstraintViolations() {
    }

    /**
     * Indica si el error se debe a la violación de la restricción única indicada. Recorre toda la
     * cadena de causas y aplica esta regla:
     * <ol>
     *   <li>Si alguna causa es una {@link ConstraintViolationException} de Hibernate con nombre de
     *       restricción, manda ese nombre: el resultado es {@code true} solo si contiene
     *       {@code constraintName} sin distinguir mayúsculas. Si hay varias con nombre, cuenta la más
     *       profunda.</li>
     *   <li>Si ninguna causa informa del nombre, basta con que alguna sea una {@link SQLException}
     *       con SQLSTATE {@code 23505}. Se asume que la tabla no tiene otra restricción única que la
     *       indicada, aparte de la clave primaria generada.</li>
     *   <li>En cualquier otro caso, {@code false}.</li>
     * </ol>
     *
     * @param error          excepción recibida al persistir; puede ser {@code null}
     * @param constraintName nombre de la restricción única esperada
     * @return {@code true} si el error corresponde a esa restricción única
     * @throws NullPointerException si {@code constraintName} es {@code null}
     */
    public static boolean isViolation(Throwable error, String constraintName) {
        Objects.requireNonNull(constraintName, "constraintName");
        String reportedName = null;
        boolean uniqueViolation = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                reportedName = violation.getConstraintName();
            }
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                uniqueViolation = true;
            }
        }
        if (reportedName != null) {
            return reportedName.toLowerCase(Locale.ROOT).contains(constraintName.toLowerCase(Locale.ROOT));
        }
        return uniqueViolation;
    }
}
