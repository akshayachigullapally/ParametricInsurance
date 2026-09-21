package org.example.claimsservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;

@SpringBootApplication
public class ClaimsserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsserviceApplication.class, args);
    }

    @Bean
    CommandLineRunner createDataDirectory() {
        return args -> {
            File dataDir = new File("./data");
            if (!dataDir.exists()) {
                boolean created = dataDir.mkdirs();
                if (created) {
                    System.out.println("Created data directory for H2 database: " + dataDir.getAbsolutePath());
                }
            }
        };
    }

}
