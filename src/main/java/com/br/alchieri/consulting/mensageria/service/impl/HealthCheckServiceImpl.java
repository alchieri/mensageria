package com.br.alchieri.consulting.mensageria.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.service.HealthCheckService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckServiceImpl implements HealthCheckService {

    private final WebClient.Builder webClientBuilder;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    private WebClient getBspWebClient() {
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }

    @Override
    public Mono<WhatsAppHealthStatusResponse> checkWhatsAppConfigStatus(Company company) {
        WebClient webClient = getBspWebClient();

        // Mono para buscar status da WABA
        Mono<JsonNode> wabaStatusMono;
        if (StringUtils.hasText(company.getMetaWabaId())) {
            String wabaEndpoint = "/" + company.getMetaWabaId();
            wabaStatusMono = webClient.get().uri(wabaEndpoint).retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> {
                        log.error("Erro ao buscar status da WABA {}: {}", company.getMetaWabaId(), e.getMessage());
                        return Mono.just(JsonNodeFactory.instance.objectNode().put("error", e.getMessage()));
                    });
        } else {
            wabaStatusMono = Mono.just(JsonNodeFactory.instance.objectNode().put("error", "WABA ID not configured."));
        }

        // Mono para buscar status do Phone Number
        Mono<JsonNode> phoneStatusMono;
        if (StringUtils.hasText(company.getMetaPrimaryPhoneNumberId())) {
            //String phoneEndpoint = "/" + company.getMetaPrimaryPhoneNumberId();
            String fields = "verified_name,quality_rating,messaging_limit_tier,code_verification_status,status,name_status";
            String finalUrl = "/" + company.getMetaPrimaryPhoneNumberId() + "?fields=" + fields;
            log.info("Tentando fazer GET para a URL da Meta: {}", webClient.get()+finalUrl);
            phoneStatusMono = webClient.get()
                    .uri(finalUrl)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> {
                        log.error("Erro ao buscar status do Phone Number {}: {}", company.getMetaPrimaryPhoneNumberId(), e.getMessage());
                        return Mono.just(JsonNodeFactory.instance.objectNode().put("error", e.getMessage()));
                    });
        } else {
            phoneStatusMono = Mono.just(JsonNodeFactory.instance.objectNode().put("error", "Phone Number ID not configured."));
        }

        // Combina os resultados das duas chamadas
        return Mono.zip(wabaStatusMono, phoneStatusMono)
            .map(tuple -> WhatsAppHealthStatusResponse.from(company, tuple.getT1(), tuple.getT2()));
    }
}
