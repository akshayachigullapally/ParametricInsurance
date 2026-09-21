package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SmsDeliveryService {

    public Mono<Void> sendSms(String phoneNumber, String text) {
        return Mono.fromRunnable(() -> log.info(
                "Simulated SMS sent to {} | text={}",
                phoneNumber, text));
    }
}