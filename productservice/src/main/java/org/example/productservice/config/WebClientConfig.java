package org.example.productservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
    @Bean("triggerServiceWebClient")
        public WebClient triggerServiceWebClient(WebClient.Builder loadBalancedWebClientBuilder){
            return loadBalancedWebClientBuilder
                    .baseUrl("http://TRIGGER-SERVICE")
                    .build();
        }

    @Bean("financialServiceWebClient")
        public WebClient financialServiceWebClient(WebClient.Builder loadBalancedWebClientBuilder){
            return loadBalancedWebClientBuilder
                    .baseUrl("http://FINANCIAL-SERVICE")
                    .build();
        }

}