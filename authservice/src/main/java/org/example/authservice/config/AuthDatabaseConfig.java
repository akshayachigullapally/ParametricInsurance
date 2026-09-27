package org.example.authservice.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the auth H2 database in one fixed folder (default: ~/.cityshield/data).
 *
 * The old URL "file:///./data/authdb" resolved against whatever working
 * directory the service was started from, so starting it from the IDE, from
 * the module folder or from a fresh copy of the project each opened a
 * different (or older) database file and registered users seemed to vanish.
 * Project folders inside OneDrive are also re-synced and locked by OneDrive,
 * which H2 does not tolerate.
 *
 * On first start with the new location, the newest existing legacy database
 * is copied over so current users are kept.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "auth.db.dir")
public class AuthDatabaseConfig {
    private static final String DB_NAME = "authdb";
    private static final String DB_FILE = DB_NAME + ".mv.db";

    @Bean
    public ConnectionFactory connectionFactory(
            @Value("${auth.db.dir}") String dbDir,
            @Value("${spring.r2dbc.username:sa}") String username,
            @Value("${spring.r2dbc.password:}") String password) {
        Path dir = Paths.get(dbDir).toAbsolutePath().normalize();
        createDirectories(dir);
        migrateLegacyDatabase(dir.resolve(DB_FILE));

        String dbPath = dir.resolve(DB_NAME).toString().replace('\\', '/');
        log.info("Auth database file: {}", dir.resolve(DB_FILE));

        // Same connection as the old "r2dbc:pool:h2:file:///...;DB_CLOSE_DELAY=-1" URL, but the
        // options are passed individually so paths with spaces work.
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "pool")
                .option(ConnectionFactoryOptions.PROTOCOL, "h2:file")
                .option(ConnectionFactoryOptions.DATABASE, dbPath + ";DB_CLOSE_DELAY=-1")
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create auth database folder " + dir, e);
        }
    }

    private void migrateLegacyDatabase(Path target) {
        if (Files.exists(target)) {
            return;
        }

        Optional<Path> legacy = List.of(
                        Paths.get("data", DB_FILE),
                        Paths.get("authservice", "data", DB_FILE),
                        Paths.get("ParametricInsurance", "authservice", "data", DB_FILE))
                .stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .max(Comparator.comparingLong(this::lastModified));

        legacy.ifPresent(source -> {
            try {
                Files.copy(source, target);
                log.info("Copied existing auth database {} to {}", source, target);
            } catch (IOException e) {
                log.warn("Could not copy legacy auth database {}: {}", source, e.getMessage());
            }
        });
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
