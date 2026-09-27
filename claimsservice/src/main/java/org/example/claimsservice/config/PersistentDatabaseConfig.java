package org.example.claimsservice.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps this service's H2 database in one fixed folder
 * (default: ~/.cityshield/data, override with CITYSHIELD_DB_DIR).
 *
 * The old URL "file:///./data/..." resolved against the folder the service was
 * started from, so a restart from another folder, a fresh copy of the project
 * or an OneDrive re-sync opened a different/older file and data "disappeared".
 * On first start the newest legacy database file is copied over.
 *
 * Only active when spring.r2dbc.url is not set, so tests keep their in-memory
 * databases.
 */
@Configuration
@ConditionalOnExpression("'${spring.r2dbc.url:}'.isEmpty()")
public class PersistentDatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(PersistentDatabaseConfig.class);
    private static final String SERVICE_FOLDER = "claimsservice";

    @Bean
    public ConnectionFactory connectionFactory(
            @Value("${app.db.dir}") String dbDir,
            @Value("${app.db.name}") String dbName,
            @Value("${spring.r2dbc.username:sa}") String username,
            @Value("${spring.r2dbc.password:}") String password) {
        Path dir = Paths.get(dbDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create database folder " + dir, e);
        }
        String fileName = dbName + ".mv.db";
        migrateLegacyDatabase(dir.resolve(fileName), fileName);
        log.info("Database file: {}", dir.resolve(fileName));

        // Same connection as the old "r2dbc:pool:h2:file:///...;SETTINGS" URL, but the
        // options are passed individually so paths with spaces work.
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "pool")
                .option(ConnectionFactoryOptions.PROTOCOL, "h2:file")
                .option(ConnectionFactoryOptions.DATABASE,
                        dir.resolve(dbName).toString().replace(File.separatorChar, '/')
                                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    private void migrateLegacyDatabase(Path target, String fileName) {
        if (Files.exists(target)) {
            return;
        }
        List.of(
                        Paths.get("data", fileName),
                        Paths.get(SERVICE_FOLDER, "data", fileName),
                        Paths.get("ParametricInsurance", SERVICE_FOLDER, "data", fileName))
                .stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .max(Comparator.comparingLong(PersistentDatabaseConfig::lastModified))
                .ifPresent(source -> {
                    try {
                        Files.copy(source, target);
                        log.info("Copied existing database {} to {}", source, target);
                    } catch (IOException e) {
                        log.warn("Could not copy legacy database {}: {}", source, e.getMessage());
                    }
                });
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
