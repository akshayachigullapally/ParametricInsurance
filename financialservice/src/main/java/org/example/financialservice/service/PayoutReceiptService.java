package org.example.financialservice.service;

import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.pdf.SimplePdfDocument;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** PDF receipt for a completed claim payout. */
@Service
public class PayoutReceiptService{
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final PayoutRepository payoutRepository;
    private final ProductServiceClient productServiceClient;

    public PayoutReceiptService(PayoutRepository payoutRepository,
                                ProductServiceClient productServiceClient){
        this.payoutRepository=payoutRepository;
        this.productServiceClient=productServiceClient;
    }

    public Mono<Receipt> receiptForCustomer(Long payoutId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->payoutRepository.findByIdAndCustomerId(payoutId,customerId))
                .switchIfEmpty(Mono.error(new NotFoundException("Payout not found")))
                .flatMap(this::render);
    }

    public Mono<Receipt> receiptForAdmin(Long payoutId){
        return SecurityUtil.requireAdmin()
                .then(payoutRepository.findById(payoutId))
                .switchIfEmpty(Mono.error(new NotFoundException("Payout not found")))
                .flatMap(this::render);
    }

    private Mono<Receipt> render(Payout payout){
        if(payout.getStatus()!=PayoutStatus.SUCCESS){
            return Mono.error(new ConflictException(
                    "A receipt is available once the payout has been completed"));
        }

        return productServiceClient.findPolicy(payout.getPolicyId())
                .map(java.util.Optional::of)
                .onErrorReturn(java.util.Optional.empty())
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(policy->Mono.fromCallable(()->build(payout,policy.orElse(null)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes->new Receipt(payout.getPayoutReference()+"-receipt.pdf",bytes));
    }

    private byte[] build(Payout payout,InternalPolicyResponse policy){
        Map<String,String> paymentRows=new LinkedHashMap<>();
        paymentRows.put("Receipt number",payout.getPayoutReference());
        paymentRows.put("Amount paid",money(payout.getAmount()));
        paymentRows.put("Status","Completed");
        paymentRows.put("Paid on",payout.getProcessedAt()!=null
                ?payout.getProcessedAt().format(DATE_TIME):"-");
        paymentRows.put("Bank / transfer reference",payout.getExternalReference());

        Map<String,String> claimRows=new LinkedHashMap<>();
        claimRows.put("Claim number",payout.getClaimNumber());
        claimRows.put("Policy number",policy!=null?policy.getPolicyNumber():"#"+payout.getPolicyId());
        if(policy!=null){
            claimRows.put("Product",policy.getProductName());
            claimRows.put("Sum insured",money(policy.getSumInsured()));
        }
        claimRows.put("Trigger event",payout.getTriggerEventId()!=null
                ?"#"+payout.getTriggerEventId():"Manual claim");
        claimRows.put("Customer ID",String.valueOf(payout.getCustomerId()));

        return new SimplePdfDocument("Payout receipt",
                "Issued "+(payout.getProcessedAt()!=null
                        ?payout.getProcessedAt().format(DATE_TIME):""))
                .section("Payment",paymentRows)
                .section("Claim",claimRows)
                .note("This payout was made under a parametric policy: it is paid because the "
                        +"insured trigger condition was met for your location zone, as verified by CityShield.")
                .note("Keep this receipt for your records. For questions, contact support quoting the receipt number.")
                .render();
    }

    private static String money(BigDecimal amount){
        if(amount==null){
            return "-";
        }
        NumberFormat format=NumberFormat.getNumberInstance(new Locale("en","IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "INR "+format.format(amount);
    }

    public record Receipt(String fileName,byte[] bytes){}
}
