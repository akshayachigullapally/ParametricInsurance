package org.example.financialservice.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class PremiumReminderScheduler{
    private final PaymentRepository paymentRepository;
    private final NotificationService notifications;

    public PremiumReminderScheduler(
            PaymentRepository paymentRepository,
            NotificationService notifications
    ){
        this.paymentRepository=paymentRepository;
        this.notifications=notifications;
    }

    @Scheduled(cron="0 0 9 * * *")
    public void sendPremiumReminders(){
        int sent=paymentRepository
                .findAllByStatus(PaymentStatus.SUCCESS)
                .collectList()
                .flatMapMany(payments->
                        Flux.fromIterable(
                                latestPerPolicy(payments).values()
                        )
                )
                .flatMap(this::sendReminder)
                .collectList()
                .block()
                .size();

        log.info("Premium reminder scheduler sent {} reminders", sent);
    }

    private Map<Long,Payment> latestPerPolicy(
            List<Payment> payments
    ){
        Map<Long,Payment> latest=new HashMap<>();

        for(Payment payment:payments){
            if(payment.getCompletedAt()==null){
                continue;
            }

            Payment existing=latest.get(payment.getPolicyId());

            if(existing==null||payment.getCompletedAt()
                    .isAfter(existing.getCompletedAt())){
                latest.put(payment.getPolicyId(),payment);
            }
        }

        return latest;
    }

    private Mono<Integer> sendReminder(Payment payment){
        LocalDateTime completedAt=payment.getCompletedAt();

        if(completedAt==null){
            return Mono.just(0);
        }

        long daysSince=ChronoUnit.DAYS.between(
                completedAt.toLocalDate(),
                LocalDate.now()
        );

        if(daysSince<7){
            return Mono.just(0);
        }

        BigDecimal amount=payment.getAmount();
        String policyNumber=payment.getPolicyNumber();
        LocalDate dueDate=LocalDate.now().plusDays(7);

        Mono<Void> reminder=daysSince>=30
                ?notifications.notifyPremiumOverdue(
                payment.getCustomerId(),
                policyNumber,
                amount,
                dueDate
        )
                :notifications.notifyPremiumDue(
                payment.getCustomerId(),
                policyNumber,
                amount,
                dueDate
        );

        return reminder.thenReturn(1);
    }
}