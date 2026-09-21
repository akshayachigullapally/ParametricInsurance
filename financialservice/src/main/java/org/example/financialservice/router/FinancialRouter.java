package org.example.financialservice.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import org.example.financialservice.handler.MetricsHandler;
import org.example.financialservice.handler.PaymentHandler;
import org.example.financialservice.handler.PayoutHandler;
import org.example.financialservice.handler.PricingRuleHandler;
import org.example.financialservice.handler.QuoteHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class FinancialRouter{
    @Bean
    RouterFunction<ServerResponse> financialRoutes(
            PricingRuleHandler pricingRuleHandler,
            QuoteHandler quoteHandler,
            PaymentHandler paymentHandler,
            PayoutHandler payoutHandler,
            MetricsHandler metricsHandler
    ){
        return RouterFunctions.route()
                .POST(
                        "/api/financial/pricing-rules",
                        accept(APPLICATION_JSON),
                        pricingRuleHandler::create
                )
                .GET(
                        "/api/financial/pricing-rules",
                        pricingRuleHandler::findAll
                )
                .GET(
                        "/api/financial/pricing-rules/{ruleId}",
                        pricingRuleHandler::findById
                )
                .PUT(
                        "/api/financial/pricing-rules/{ruleId}",
                        accept(APPLICATION_JSON),
                        pricingRuleHandler::update
                )
                .PUT(
                        "/api/financial/pricing-rules/{ruleId}/status",
                        accept(APPLICATION_JSON),
                        pricingRuleHandler::updateStatus
                )
                .DELETE(
                        "/api/financial/pricing-rules/{ruleId}",
                        pricingRuleHandler::archive
                )
                .POST(
                        "/api/financial/quotes",
                        accept(APPLICATION_JSON),
                        quoteHandler::create
                )
                .GET(
                        "/api/financial/quotes/me",
                        quoteHandler::findMine
                )
                .GET(
                        "/api/financial/quotes/{quoteId}",
                        quoteHandler::findById
                )
                .PUT(
                        "/api/financial/quotes/{quoteId}/accept",
                        quoteHandler::accept
                )
                .PUT(
                        "/api/financial/quotes/{quoteId}/cancel",
                        quoteHandler::cancel
                )
                .POST(
                        "/internal/financial/quotes/{quoteId}/validate",
                        accept(APPLICATION_JSON),
                        quoteHandler::validateInternal
                )
                .PUT(
                        "/internal/financial/quotes/{quoteId}/consume",
                        accept(APPLICATION_JSON),
                        quoteHandler::consumeInternal
                )
                .POST(
                        "/api/financial/payments",
                        accept(APPLICATION_JSON),
                        paymentHandler::create
                )
                .GET(
                        "/api/financial/payments/me",
                        paymentHandler::findMine
                )
                .GET(
                        "/api/financial/payments/{paymentId}",
                        paymentHandler::findById
                )
                .GET(
                        "/api/financial/payments/admin/all",
                        paymentHandler::findAll
                )
                .PUT(
                        "/api/financial/payments/{paymentId}/success",
                        accept(APPLICATION_JSON),
                        paymentHandler::success
                )
                .PUT(
                        "/api/financial/payments/{paymentId}/fail",
                        accept(APPLICATION_JSON),
                        paymentHandler::fail
                )
                .PUT(
                        "/api/financial/payments/{paymentId}/refund",
                        paymentHandler::refund
                )
                .PUT(
                        "/api/financial/payments/{paymentId}/retry",
                        paymentHandler::retry
                )
                .GET(
                        "/internal/financial/payments/policy/{policyId}/status",
                        paymentHandler::policyStatus
                )
                .POST(
                        "/api/financial/payouts",
                        accept(APPLICATION_JSON),
                        payoutHandler::create
                )
                .GET(
                        "/api/financial/payouts/me",
                        payoutHandler::findMine
                )
                .GET(
                        "/api/financial/payouts/{payoutId}",
                        payoutHandler::findById
                )
                .GET(
                        "/api/financial/payouts/admin/all",
                        payoutHandler::findAll
                )
                .PUT(
                        "/api/financial/payouts/{payoutId}/process",
                        accept(APPLICATION_JSON),
                        payoutHandler::process
                )
                .PUT(
                        "/api/financial/payouts/{payoutId}/success",
                        accept(APPLICATION_JSON),
                        payoutHandler::success
                )
                .PUT(
                        "/api/financial/payouts/{payoutId}/fail",
                        accept(APPLICATION_JSON),
                        payoutHandler::fail
                )
                .PUT(
                        "/api/financial/payouts/{payoutId}/retry",
                        accept(APPLICATION_JSON),
                        payoutHandler::retry
                )
                .POST(
                        "/internal/financial/payouts",
                        accept(APPLICATION_JSON),
                        payoutHandler::createInternal
                )
                .GET(
                        "/internal/financial/payouts/claim/{claimId}",
                        payoutHandler::findByClaim
                )
                .GET(
                        "/api/financial/admin/metrics",
                        metricsHandler::metrics
                )
                .build();
    }
}