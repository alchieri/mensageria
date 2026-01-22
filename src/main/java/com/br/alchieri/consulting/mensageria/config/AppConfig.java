package com.br.alchieri.consulting.mensageria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Você pode adicionar configurações extras aqui (timeouts, interceptors, etc.)
        return new RestTemplate();
    }
}
