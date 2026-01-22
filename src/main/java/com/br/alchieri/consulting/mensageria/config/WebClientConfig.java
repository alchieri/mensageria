package com.br.alchieri.consulting.mensageria.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    // Timeout de conexão e resposta (exemplo)
    private static final int CONNECT_TIMEOUT_MS = 20000; // 20 segundos
    private static final int RESPONSE_TIMEOUT_SECONDS = 15; // 15 segundos

    @Bean
    public WebClient.Builder metaApiWebClientBuilder() { // Renomeado para clareza
        log.info("Configurando WebClient.Builder para Meta API com Base URL: {}", graphApiBaseUrl);

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS);

        return WebClient.builder()
                .baseUrl(graphApiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        // O token de autorização (System User Token do BSP) será adicionado nos serviços
    }
}
