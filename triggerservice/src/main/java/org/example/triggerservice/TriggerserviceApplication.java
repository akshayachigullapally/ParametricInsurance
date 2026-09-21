package org.example.triggerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TriggerserviceApplication{

    public static void main(String[] args){
        SpringApplication.run(TriggerserviceApplication.class, args);
    }

}
