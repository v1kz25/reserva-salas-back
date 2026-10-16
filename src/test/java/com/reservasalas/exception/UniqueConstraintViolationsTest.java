package com.reservasalas.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UniqueConstraintViolationsTest {

    private static final String CONSTRAINT = "UK_ROOMS_NAME_KEY";
    private static final String UNIQUE_STATE = "23505";

    @Test
    void reconoceElNombreExactoDeLaRestriccion() {
        Throwable error = wrap(hibernateViolation(CONSTRAINT, UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isTrue();
    }

    @Test
    void reconoceElNombreSinDistinguirMayusculas() {
        Throwable error = wrap(hibernateViolation("uk_rooms_name_key", UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isTrue();
    }

    @Test
    void reconoceElNombreConPrefijoOSufijo() {
        Throwable withPrefix = wrap(hibernateViolation("PUBLIC." + CONSTRAINT, UNIQUE_STATE));
        Throwable withSuffix = wrap(hibernateViolation(CONSTRAINT + "_INDEX_4", UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(withPrefix, CONSTRAINT)).isTrue();
        assertThat(UniqueConstraintViolations.isViolation(withSuffix, CONSTRAINT)).isTrue();
    }

    @Test
    void otroNombreDeRestriccionNoCuentaAunqueElSqlStateSea23505() {
        Throwable error = wrap(hibernateViolation("UK_USERS_EMAIL", UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isFalse();
    }

    @Test
    void sqlState23505SinNombreDeRestriccionCuenta() {
        Throwable error = wrap(new SQLException("Unique index violation", UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isTrue();
    }

    @Test
    void violacionDeHibernateSinNombreConSqlState23505Cuenta() {
        Throwable error = wrap(hibernateViolation(null, UNIQUE_STATE));

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isTrue();
    }

    @Test
    void otroSqlStateSinNombreNoCuenta() {
        Throwable notNull = wrap(new SQLException("Not null violation", "23502"));
        Throwable foreignKey = wrap(hibernateViolation(null, "23503"));

        assertThat(UniqueConstraintViolations.isViolation(notNull, CONSTRAINT)).isFalse();
        assertThat(UniqueConstraintViolations.isViolation(foreignKey, CONSTRAINT)).isFalse();
    }

    @Test
    void recorreUnaCadenaDeCausasProfunda() {
        Throwable error = new SQLException("23505", UNIQUE_STATE);
        for (int i = 0; i < 10; i++) {
            error = new IllegalStateException("nivel " + i, error);
        }
        Throwable named = hibernateViolation(CONSTRAINT, UNIQUE_STATE);
        for (int i = 0; i < 10; i++) {
            named = new IllegalStateException("nivel " + i, named);
        }

        assertThat(UniqueConstraintViolations.isViolation(wrap(error), CONSTRAINT)).isTrue();
        assertThat(UniqueConstraintViolations.isViolation(wrap(named), CONSTRAINT)).isTrue();
    }

    @Test
    void sinCausasRelevantesNoCuenta() {
        Throwable error = new DataIntegrityViolationException("sin causa");

        assertThat(UniqueConstraintViolations.isViolation(error, CONSTRAINT)).isFalse();
    }

    @Test
    void errorNuloNoCuenta() {
        assertThat(UniqueConstraintViolations.isViolation(null, CONSTRAINT)).isFalse();
    }

    @Test
    void nombreDeRestriccionNuloLanzaExcepcion() {
        Throwable error = wrap(new SQLException("Unique index violation", UNIQUE_STATE));

        assertThatNullPointerException()
                .isThrownBy(() -> UniqueConstraintViolations.isViolation(error, null));
    }

    private static ConstraintViolationException hibernateViolation(String constraintName, String sqlState) {
        SQLException sqlException = new SQLException("violación", sqlState);
        return new ConstraintViolationException("violación", sqlException, constraintName);
    }

    private static DataIntegrityViolationException wrap(Throwable cause) {
        return new DataIntegrityViolationException("integridad", cause);
    }
}
