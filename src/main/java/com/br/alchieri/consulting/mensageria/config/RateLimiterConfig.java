package com.br.alchieri.consulting.mensageria.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

@Configuration
public class RateLimiterConfig {

    // Pode ler do application.properties se quiser tornar configurável
    @Value("${whatsapp.meta.api.rate-limit.capacity:5}") // Ex: Capacidade inicial de 5 tokens
    private long capacity;

    @Value("${whatsapp.meta.api.rate-limit.refill-tokens:5}") // Ex: Adiciona 5 tokens...
    private long refillTokens;

    @Value("${whatsapp.meta.api.rate-limit.refill-period-seconds:1}") // Ex: ...a cada 1 segundo
    private long refillPeriodSeconds;

    @SuppressWarnings("deprecation")
    @Bean(name = "metaApiRateLimiterBucket") // Nome específico para o bucket
    public Bucket metaApiRateLimiterBucket() {
        // Define a largura de banda: X tokens adicionados a cada Y período de tempo
        Refill refill = Refill.intervally(refillTokens, Duration.ofSeconds(refillPeriodSeconds));
        // Define o limite: Capacidade inicial e como é reabastecido
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        // Cria o bucket (local na memória - thread-safe)
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
