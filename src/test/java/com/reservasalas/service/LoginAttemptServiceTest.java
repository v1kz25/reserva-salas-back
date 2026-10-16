package com.reservasalas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reservasalas.config.LoginAttemptProperties;
import com.reservasalas.exception.TooManyLoginAttemptsException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String IP = "10.0.0.1";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-16T10:00:00Z"));
        service = new LoginAttemptService(clock, new LoginAttemptProperties(3, 15, 100));
    }

    @Test
    void registerAttempt_whenLimitReached_rejectsWithRemainingSecondsRoundedUp() {
        exhaust(EMAIL, IP);
        clock.advance(Duration.ofMinutes(5).plusMillis(500));

        assertThatThrownBy(() -> service.registerAttempt(EMAIL, IP))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void registerAttempt_whenWindowEnds_allowsAgain() {
        exhaust(EMAIL, IP);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> service.registerAttempt(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void registerAttempt_normalizesEmailAndSeparatesByIp() {
        exhaust(EMAIL, IP);

        assertThatThrownBy(() -> service.registerAttempt("  ANA@example.com ", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> service.registerAttempt(EMAIL, "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void reset_clearsAttempts() {
        service.registerAttempt(EMAIL, IP);
        service.registerAttempt(EMAIL, IP);
        service.reset(EMAIL, IP);

        assertThatCode(() -> exhaust(EMAIL, IP)).doesNotThrowAnyException();
    }

    private static LoginAttemptService small(MutableClock clock, int maxEntries) {
        return new LoginAttemptService(clock, new LoginAttemptProperties(2, 15, maxEntries));
    }

    @SuppressWarnings("unchecked")
    private static int size(LoginAttemptService target) {
        try {
            Field field = LoginAttemptService.class.getDeclaredField("attempts");
            field.setAccessible(true);
            return ((Map<String, ?>) field.get(target)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void blockSeries(LoginAttemptService target, String email) {
        target.registerAttempt(email, IP);
        target.registerAttempt(email, IP);
    }

    private static boolean isBlocked(LoginAttemptService target, String email) {
        try {
            target.registerAttempt(email, IP);
            return false;
        } catch (TooManyLoginAttemptsException e) {
            return true;
        }
    }

    @Test
    void registryFull_sizeNeverExceedsMaxEntries() {
        var limited = small(clock, 3);
        for (int i = 0; i < 20; i++) {
            limited.registerAttempt("u" + i + "@example.com", IP);
            clock.advance(Duration.ofSeconds(1));
            assertThat(size(limited)).isLessThanOrEqualTo(3);
        }
        assertThat(size(limited)).isEqualTo(3);
    }

    @Test
    void registryFull_prefersExpiredSeriesOverOlderUnblockedOnes() {
        var limited = small(clock, 3);
        blockSeries(limited, "a@example.com");
        clock.advance(Duration.ofMinutes(5));
        limited.registerAttempt("b@example.com", IP);
        clock.advance(Duration.ofMinutes(1));
        limited.registerAttempt("c@example.com", IP);
        clock.advance(Duration.ofMinutes(9));

        limited.registerAttempt("d@example.com", IP);

        // b y c conservan su cuenta: el segundo intento pasa y el tercero se rechaza
        for (String kept : new String[] {"b@example.com", "c@example.com"}) {
            assertThatCode(() -> limited.registerAttempt(kept, IP)).doesNotThrowAnyException();
            assertThat(isBlocked(limited, kept)).isTrue();
        }
        assertThat(size(limited)).isEqualTo(3);
    }

    @Test
    void registryFull_evictsOldestUnblockedAndKeepsBlockedSeries() {
        var limited = small(clock, 3);
        blockSeries(limited, "a@example.com");
        clock.advance(Duration.ofSeconds(1));
        limited.registerAttempt("b@example.com", IP);
        clock.advance(Duration.ofSeconds(1));
        limited.registerAttempt("c@example.com", IP);
        clock.advance(Duration.ofSeconds(1));

        limited.registerAttempt("d@example.com", IP);

        assertThat(size(limited)).isEqualTo(3);
        assertThat(isBlocked(limited, "a@example.com")).isTrue();
        for (String kept : new String[] {"c@example.com", "d@example.com"}) {
            assertThatCode(() -> limited.registerAttempt(kept, IP)).doesNotThrowAnyException();
            assertThat(isBlocked(limited, kept)).isTrue();
        }
    }

    @Test
    void registryFullOfBlockedSeries_newSeriesIsNotRegisteredAndBlockedOnesStayBlocked() {
        var limited = small(clock, 2);
        blockSeries(limited, "a@example.com");
        clock.advance(Duration.ofSeconds(1));
        blockSeries(limited, "b@example.com");

        for (int i = 0; i < 10; i++) {
            assertThatCode(() -> limited.registerAttempt("nuevo@example.com", IP)).doesNotThrowAnyException();
        }

        assertThat(size(limited)).isEqualTo(2);
        assertThat(isBlocked(limited, "a@example.com")).isTrue();
        assertThat(isBlocked(limited, "b@example.com")).isTrue();
    }

    @Test
    void registryFull_underConcurrencyRespectsMaxEntries() throws Exception {
        int maxEntries = 25;
        var limited = small(clock, maxEntries);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger maxSeen = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                int id = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) {
                        try {
                            limited.registerAttempt("u" + id + "-" + i + "@example.com", IP);
                        } catch (TooManyLoginAttemptsException ignored) {
                            // no relevante para este test
                        }
                        maxSeen.accumulateAndGet(size(limited), Math::max);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxSeen.get()).isLessThanOrEqualTo(maxEntries);
        assertThat(size(limited)).isLessThanOrEqualTo(maxEntries);
    }

    @Test
    void purgeExpired_removesOnlyExpiredSeries() {
        var limited = small(clock, 100);
        limited.registerAttempt("a@example.com", IP);
        limited.registerAttempt("b@example.com", IP);
        clock.advance(Duration.ofMinutes(10));
        limited.registerAttempt("c@example.com", IP);
        clock.advance(Duration.ofMinutes(5));

        limited.purgeExpired();

        assertThat(size(limited)).isEqualTo(1);
        assertThatCode(() -> limited.registerAttempt("c@example.com", IP)).doesNotThrowAnyException();
        assertThat(isBlocked(limited, "c@example.com")).isTrue();
    }

    private void exhaust(String email, String ip) {
        for (int i = 0; i < 3; i++) {
            service.registerAttempt(email, ip);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
