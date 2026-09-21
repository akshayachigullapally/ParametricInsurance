package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class EmailDeliveryService {

    public Mono<Void> sendEmail(String recipient, String subject, String body) {
        return Mono.fromRunnable(() -> log.info(
                "Simulated email sent to {} | subject={} | body={}",
                recipient, subject, body));
    }
}