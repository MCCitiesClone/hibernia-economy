package io.paradaux.treasury.model.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the trivial {@code @ConfigurationComponent} POJOs that aren't
 * exercised elsewhere. Verifies the no-arg constructor + getter contract that
 * Hibernia's {@code ConfigurationLoader} relies on.
 */
class ConfigPojoSmokeTest {

    @Test
    void databaseConfiguration_constructsAndExposesFields() throws Exception {
        DatabaseConfiguration cfg = construct(DatabaseConfiguration.class);
        set(cfg, "host", "db.example.test");
        set(cfg, "port", "3306");
        set(cfg, "database", "treasury");
        set(cfg, "username", "alice");
        set(cfg, "password", "secret");

        assertThat(cfg.getHost()).isEqualTo("db.example.test");
        assertThat(cfg.getPort()).isEqualTo("3306");
        assertThat(cfg.getDatabase()).isEqualTo("treasury");
        assertThat(cfg.getUsername()).isEqualTo("alice");
        assertThat(cfg.getPassword()).isEqualTo("secret");
    }

    @Test
    void loggingConfiguration_constructsAndExposesLevel() throws Exception {
        LoggingConfiguration cfg = construct(LoggingConfiguration.class);
        set(cfg, "level", "DEBUG");
        assertThat(cfg.getLevel()).isEqualTo("DEBUG");
    }

    private static <T> T construct(Class<T> clazz) throws Exception {
        Constructor<T> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
