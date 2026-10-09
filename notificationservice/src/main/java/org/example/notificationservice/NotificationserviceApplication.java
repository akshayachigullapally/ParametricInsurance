package org.example.notificationservice;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NotificationserviceApplication {

    /**
     * Fallbacks for the settings the service cannot start without. application.yaml always
     * wins; these only apply when it is not on the classpath (e.g. a stale IDE build), so the
     * service still starts on its own port, name and database instead of failing.
     */
    static final Map<String, Object> FALLBACK_PROPERTIES = Map.of(
            "spring.application.name", "NOTIFICATION-SERVICE",
            "server.port", "8085",
            "spring.r2dbc.url", "r2dbc:h2:file:///./data/notificationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "spring.r2dbc.username", "sa",
            "spring.sql.init.mode", "always",
            "eureka.client.service-url.defaultZone", "http://localhost:8761/eureka/",
            "eureka.instance.hostname", "localhost",
            "eureka.instance.prefer-ip-address", "false",
            "gateway.internal-key", "climatesure-local-gateway-key"
    );

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NotificationserviceApplication.class);
        application.setDefaultProperties(FALLBACK_PROPERTIES);
        application.run(args);
    }

}
