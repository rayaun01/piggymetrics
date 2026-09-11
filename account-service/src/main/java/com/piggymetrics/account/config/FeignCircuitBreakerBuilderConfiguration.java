package com.piggymetrics.account.config;

import feign.Client;
import feign.Feign;
import org.springframework.cloud.openfeign.FeignCircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class FeignCircuitBreakerBuilderConfiguration {

    @Bean
    @Scope("prototype")
    public Feign.Builder feignBuilder(Client client) {
        return FeignCircuitBreaker.builder().client(client);
    }

}
