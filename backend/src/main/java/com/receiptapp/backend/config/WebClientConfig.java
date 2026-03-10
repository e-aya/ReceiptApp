package com.receiptapp.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // バッファ上限を 10MB に拡張
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(10 * 1024 * 1024)  // 10MB
                )
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
