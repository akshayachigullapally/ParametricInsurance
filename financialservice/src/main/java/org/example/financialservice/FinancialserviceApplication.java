package org.example.financialservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinancialserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialserviceApplication.class, args);
    }

}
