package org.example.financialservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import org.example.financialservice.dto.CreateQuoteRequest;
import org.example.financialservice.dto.InternalQuoteConsumeRequest;
import org.example.financialservice.dto.InternalQuoteResponse;
import org.example.financialservice.dto.InternalQuoteValidateRequest;
import org.example.financialservice.dto.QuoteResponse;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.model.Quote;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class QuoteService{
    private final QuoteRepository quoteRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ProductServiceClient productServiceClient;
    private final TriggerServiceClient triggerServiceClient;
    private final NotificationService notifications;
    private final int expiryDays;

    public QuoteService(
            QuoteRepository quoteRepository,
            PricingRuleRepository pricingRuleRepository,
            ProductServiceClient productServiceClient,
            TriggerServiceClient triggerServiceClient,
            NotificationService notifications,
            @Value("${financial.quote.expiry-days:7}") int expiryDays
    ){
        this.quoteRepository=quoteRepository;
        this.pricingRuleRepository=pricingRuleRepository;
        this.productServiceClient=productServiceClient;
        this.triggerServiceClient=triggerServiceClient;
        this.notifications=notifications;
        this.expiryDays=expiryDays;
    }

    public Mono<QuoteResponse> create(CreateQuoteRequest request){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->productServiceClient
                        .findProduct(request.getProductId())
                        .flatMap(product->{
                            validateProductLimits(request,product);
                            return triggerServiceClient
                                    .requireActiveZone(
                                            request.getLocationZoneId()
                                    );
                        })
                        .then(findApplicableRule(
                                request.getProductId(),
                                request.getLocationZoneId()
                        ))
                        .flatMap(rule->{
                            BigDecimal premium=calculatePremium(
                                    request,
                                    rule
                            );

                            Quote quote=Quote.builder()
                                    .quoteReference(generateReference())
                                    .customerId(customerId)
                                    .productId(
                                            request.getProductId()
                                    )
                                    .locationZoneId(
                                            request.getLocationZoneId()
                                    )
                                    .sumInsured(
                                            request.getSumInsured()
                                    )
                                    .termMonths(
                                            request.getTermMonths()
                                    )
                                    .annualRate(
                                            rule.getBaseAnnualRate()
                                    )
                                    .termFactor(rule.getTermFactor())
                                    .locationFactor(
                                            rule.getLocationFactor()
                                    )
                                    .premiumAmount(premium)
                                    .status(QuoteStatus.CREATED)
                                    .expiresAt(
                                            LocalDateTime.now()
                                                    .plusDays(expiryDays)
                                    )
                                    .createdAt(LocalDateTime.now())
                                    .build();

                            return quoteRepository.save(quote);
                        })
                )
                .flatMap(saved->notifications.audit(
                        "QUOTE_CREATED",
                        "QUOTE",
                        saved.getId(),
                        saved.getCustomerId(),
                        "productId="+saved.getProductId()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyQuoteCreated(
                                saved.getCustomerId(),
                                saved.getQuoteReference(),
                                saved.getPremiumAmount(),
                                saved.getId()
                        )
                        .thenReturn(saved))
                .map(this::response);
    }

    public Mono<QuoteResponse> findMine(Long quoteId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->quoteRepository
                        .findByIdAndCustomerId(
                                quoteId,
                                customerId
                        )
                )
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException("Quote not found")
                        )
                )
                .flatMap(this::expireIfRequired)
                .map(this::response);
    }

    public Flux<QuoteResponse> findMine(){
        return SecurityUtil.requireCustomer()
                .flatMapMany(customerId->quoteRepository
                        .findAllByCustomerIdOrderByCreatedAtDesc(
                                customerId
                        )
                )
                .concatMap(this::expireIfRequired)
                .map(this::response);
    }

    public Mono<QuoteResponse> accept(Long quoteId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->quoteRepository
                        .findByIdAndCustomerId(
                                quoteId,
                                customerId
                        )
                )
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException("Quote not found")
                        )
                )
                .flatMap(this::expireIfRequired)
                .flatMap(quote->{
                    if(quote.getStatus()==QuoteStatus.CONSUMED){
                        return Mono.error(
                                new ConflictException(
                                        "Quote has been consumed"
                                )
                        );
                    }

                    if(quote.getStatus()==QuoteStatus.EXPIRED){
                        return Mono.error(
                                new ConflictException("Quote has expired")
                        );
                    }

                    if(quote.getStatus()==QuoteStatus.CANCELLED){
                        return Mono.error(
                                new ConflictException(
                                        "Quote is cancelled"
                                )
                        );
                    }

                    if(quote.getStatus()==QuoteStatus.ACCEPTED){
                        return Mono.just(quote);
                    }

                    quote.setStatus(QuoteStatus.ACCEPTED);
                    quote.setAcceptedAt(LocalDateTime.now());

                    return quoteRepository.save(quote);
                })
                .flatMap(saved->notifications.audit(
                        "QUOTE_ACCEPTED",
                        "QUOTE",
                        saved.getId(),
                        saved.getCustomerId(),
                        "quoteReference="+saved.getQuoteReference()
                ).thenReturn(saved))
                .map(this::response);
    }

    public Mono<QuoteResponse> cancel(Long quoteId){
        return SecurityUtil.requireCustomer()
                .flatMap(customerId->quoteRepository
                        .findByIdAndCustomerId(
                                quoteId,
                                customerId
                        )
                )
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException("Quote not found")
                        )
                )
                .flatMap(quote->{
                    if(quote.getStatus()==QuoteStatus.CANCELLED){
                        return Mono.just(quote);
                    }

                    if(quote.getStatus()==QuoteStatus.ACCEPTED){
                        return Mono.error(
                                new ConflictException(
                                        "Accepted quote cannot be cancelled"
                                )
                        );
                    }

                    if(quote.getStatus()==QuoteStatus.CONSUMED){
                        return Mono.error(
                                new ConflictException(
                                        "Consumed quote cannot be cancelled"
                                )
                        );
                    }

                    quote.setStatus(QuoteStatus.CANCELLED);

                    return quoteRepository.save(quote);
                })
                .flatMap(saved->notifications.audit(
                        "QUOTE_CANCELLED",
                        "QUOTE",
                        saved.getId(),
                        saved.getCustomerId(),
                        "quoteReference="+saved.getQuoteReference()
                ).thenReturn(saved))
                .map(this::response);
    }

    public Mono<InternalQuoteResponse> validateInternal(
            Long quoteId,
            InternalQuoteValidateRequest request
    ){
        return quoteRepository.findById(quoteId)
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException("Quote not found")
                        )
                )
                .flatMap(this::expireIfRequired)
                .map(quote->internalResponse(
                        quote,
                        request
                ));
    }

    public Mono<InternalQuoteResponse> consume(
            Long quoteId,
            InternalQuoteConsumeRequest request
    ){
        return quoteRepository.findById(quoteId)
                .switchIfEmpty(
                        Mono.error(
                                new NotFoundException("Quote not found")
                        )
                )
                .flatMap(quote->{
                    if(quote.getStatus()==QuoteStatus.CONSUMED){
                        if(quote.getPolicyId()!=null&&
                                quote.getPolicyId().equals(
                                        request.getPolicyId()
                                )){
                            return Mono.just(quote);
                        }

                        return Mono.error(
                                new ConflictException(
                                        "Quote is already consumed by another policy"
                                )
                        );
                    }

                    if(quote.getStatus()!=QuoteStatus.ACCEPTED){
                        return Mono.error(
                                new ConflictException(
                                        "Quote must be accepted before consumption"
                                )
                        );
                    }

                    if(quote.getExpiresAt()
                            .isBefore(LocalDateTime.now())){
                        quote.setStatus(QuoteStatus.EXPIRED);

                        return quoteRepository.save(quote)
                                .then(Mono.<Quote>error(
                                        new ConflictException(
                                                "Quote has expired"
                                        )
                                ));
                    }

                    return quoteRepository
                            .findAllByPolicyIdAndStatus(
                                    request.getPolicyId(),
                                    QuoteStatus.CONSUMED
                            )
                            .filter(other->
                                    !other.getId().equals(
                                            quote.getId()
                                    )
                            )
                            .hasElements()
                            .flatMap(otherConsumed->{
                                if(otherConsumed){
                                    return Mono.error(
                                            new ConflictException(
                                                    "Policy already bound to another quote"
                                            )
                                    );
                                }

                                quote.setPolicyId(
                                        request.getPolicyId()
                                );
                                quote.setPolicyNumber(
                                        request.getPolicyNumber()
                                );
                                quote.setStatus(QuoteStatus.CONSUMED);
                                quote.setConsumedAt(
                                        LocalDateTime.now()
                                );

                                return quoteRepository.save(quote);
                            });
                })
                .flatMap(saved->notifications.audit(
                        "QUOTE_CONSUMED",
                        "QUOTE",
                        saved.getId(),
                        saved.getCustomerId(),
                        "policyId="+saved.getPolicyId()
                ).thenReturn(saved))
                .flatMap(saved->notifications
                        .notifyQuoteConsumed(
                                saved.getCustomerId(),
                                saved.getQuoteReference(),
                                saved.getPolicyNumber(),
                                saved.getId()
                        )
                        .thenReturn(saved))
                .map(this::internalResponse);
    }

    private void validateProductLimits(
            CreateQuoteRequest request,
            InternalProductResponse product
    ){
        if(product.getMinSumInsured()!=null&&
                request.getSumInsured()
                        .compareTo(product.getMinSumInsured())<0){
            throw new ValidationException(
                    "Sum insured is below minimum"
            );
        }

        if(product.getMaxSumInsured()!=null&&
                request.getSumInsured()
                        .compareTo(product.getMaxSumInsured())>0){
            throw new ValidationException(
                    "Sum insured exceeds maximum"
            );
        }

        if(product.getMinTermMonths()!=null&&
                request.getTermMonths()<
                        product.getMinTermMonths()){
            throw new ValidationException(
                    "Term is below minimum"
            );
        }

        if(product.getMaxTermMonths()!=null&&
                request.getTermMonths()>
                        product.getMaxTermMonths()){
            throw new ValidationException(
                    "Term exceeds maximum"
            );
        }
    }

    private Mono<PricingRule> findApplicableRule(
            Long productId,
            Long locationZoneId
    ){
        LocalDate today=LocalDate.now();

        return pricingRuleRepository
                .findAllByProductIdAndStatus(
                        productId,
                        PricingRuleStatus.ACTIVE
                )
                .filter(rule->
                        !rule.getEffectiveFrom().isAfter(today)
                )
                .filter(rule->
                        rule.getEffectiveTo()==null||
                                !rule.getEffectiveTo()
                                        .isBefore(today)
                )
                .collectList()
                .flatMap(rules->{
                    PricingRule zoneRule=rules.stream()
                            .filter(rule->
                                    locationZoneId.equals(
                                            rule.getLocationZoneId()
                                    )
                            )
                            .max(Comparator.comparing(
                                    PricingRule::getEffectiveFrom
                            ))
                            .orElse(null);

                    if(zoneRule!=null){
                        return Mono.just(zoneRule);
                    }

                    PricingRule defaultRule=rules.stream()
                            .filter(rule->
                                    rule.getLocationZoneId()==null
                            )
                            .max(Comparator.comparing(
                                    PricingRule::getEffectiveFrom
                            ))
                            .orElse(null);

                    if(defaultRule!=null){
                        return Mono.just(defaultRule);
                    }

                    return Mono.error(
                            new NotFoundException(
                                    "No active pricing rule found for product"
                            )
                    );
                });
    }

    private BigDecimal calculatePremium(
            CreateQuoteRequest request,
            PricingRule rule
    ){
        BigDecimal termYears=BigDecimal.valueOf(
                request.getTermMonths()
        ).divide(
                BigDecimal.valueOf(12),
                10,
                RoundingMode.HALF_UP
        );

        BigDecimal calculated=request.getSumInsured()
                .multiply(rule.getBaseAnnualRate())
                .multiply(termYears)
                .multiply(rule.getTermFactor())
                .multiply(rule.getLocationFactor())
                .setScale(2,RoundingMode.HALF_UP);

        return calculated.max(rule.getMinimumPremium())
                .setScale(2,RoundingMode.HALF_UP);
    }

    private Mono<Quote> expireIfRequired(Quote quote){
        if(quote.getStatus()==QuoteStatus.CREATED&&
                quote.getExpiresAt().isBefore(
                        LocalDateTime.now()
                )){
            quote.setStatus(QuoteStatus.EXPIRED);

            return quoteRepository.save(quote)
                    .flatMap(saved->notifications
                            .notifyQuoteExpired(
                                    saved.getCustomerId(),
                                    saved.getQuoteReference(),
                                    saved.getId()
                            )
                            .thenReturn(saved));
        }

        return Mono.just(quote);
    }

    private String generateReference(){
        return "QTE-"+
                UUID.randomUUID()
                        .toString()
                        .replace("-","")
                        .substring(0,16)
                        .toUpperCase();
    }

    private InternalQuoteResponse internalResponse(
            Quote quote,
            InternalQuoteValidateRequest request
    ){
        String reason=null;
        boolean valid=true;

        if(!quote.getCustomerId()
                .equals(request.getCustomerId())){
            reason="Quote does not belong to customer";
            valid=false;
        }else if(!quote.getProductId()
                .equals(request.getProductId())){
            reason="ProductId mismatch";
            valid=false;
        }else if(!quote.getLocationZoneId()
                .equals(request.getLocationZoneId())){
            reason="LocationZoneId mismatch";
            valid=false;
        }else if(quote.getSumInsured()
                .compareTo(request.getSumInsured())!=0){
            reason="SumInsured mismatch";
            valid=false;
        }else if(!quote.getTermMonths()
                .equals(request.getTermMonths())){
            reason="TermMonths mismatch";
            valid=false;
        }else if(quote.getStatus()!=QuoteStatus.ACCEPTED){
            reason="Quote is not accepted";
            valid=false;
        }else if(quote.getStatus()==QuoteStatus.CONSUMED){
            reason="Quote has been consumed";
            valid=false;
        }else if(quote.getExpiresAt().isBefore(
                LocalDateTime.now()
        )){
            reason="Quote has expired";
            valid=false;
        }else{
            reason="Quote is valid";
        }

        return InternalQuoteResponse.builder()
                .quoteId(quote.getId())
                .quoteReference(quote.getQuoteReference())
                .customerId(quote.getCustomerId())
                .productId(quote.getProductId())
                .locationZoneId(quote.getLocationZoneId())
                .sumInsured(quote.getSumInsured())
                .termMonths(quote.getTermMonths())
                .premiumAmount(quote.getPremiumAmount())
                .status(quote.getStatus())
                .expiresAt(quote.getExpiresAt())
                .policyId(quote.getPolicyId())
                .policyNumber(quote.getPolicyNumber())
                .consumedAt(quote.getConsumedAt())
                .valid(valid)
                .validationReason(reason)
                .build();
    }

    private InternalQuoteResponse internalResponse(Quote quote){
        return InternalQuoteResponse.builder()
                .quoteId(quote.getId())
                .quoteReference(quote.getQuoteReference())
                .customerId(quote.getCustomerId())
                .productId(quote.getProductId())
                .locationZoneId(quote.getLocationZoneId())
                .sumInsured(quote.getSumInsured())
                .termMonths(quote.getTermMonths())
                .premiumAmount(quote.getPremiumAmount())
                .status(quote.getStatus())
                .expiresAt(quote.getExpiresAt())
                .policyId(quote.getPolicyId())
                .policyNumber(quote.getPolicyNumber())
                .consumedAt(quote.getConsumedAt())
                .build();
    }

    private QuoteResponse response(Quote quote){
        return QuoteResponse.builder()
                .id(quote.getId())
                .quoteReference(quote.getQuoteReference())
                .customerId(quote.getCustomerId())
                .productId(quote.getProductId())
                .locationZoneId(quote.getLocationZoneId())
                .sumInsured(quote.getSumInsured())
                .termMonths(quote.getTermMonths())
                .annualRate(quote.getAnnualRate())
                .termFactor(quote.getTermFactor())
                .locationFactor(quote.getLocationFactor())
                .premiumAmount(quote.getPremiumAmount())
                .status(quote.getStatus())
                .expiresAt(quote.getExpiresAt())
                .createdAt(quote.getCreatedAt())
                .acceptedAt(quote.getAcceptedAt())
                .policyId(quote.getPolicyId())
                .policyNumber(quote.getPolicyNumber())
                .consumedAt(quote.getConsumedAt())
                .build();
    }
}