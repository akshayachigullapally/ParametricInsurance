package org.example.financialservice.router;

import org.example.financialservice.handler.PayoutReceiptHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class PayoutReceiptRouter{
    @Bean
    public RouterFunction<ServerResponse> payoutReceiptRoutes(PayoutReceiptHandler handler){
        return RouterFunctions.route()
                .GET("/api/financial/payouts/{payoutId}/receipt",handler::customerReceipt)
                .GET("/api/financial/payouts/admin/{payoutId}/receipt",handler::adminReceipt)
                .build();
    }
}
