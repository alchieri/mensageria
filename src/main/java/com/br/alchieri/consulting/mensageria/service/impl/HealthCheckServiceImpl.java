package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse.ChannelStatus;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.repository.WhatsAppPhoneNumberRepository;
import com.br.alchieri.consulting.mensageria.service.HealthCheckService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckServiceImpl implements HealthCheckService {

    private final WebClient.Builder webClientBuilder;

    private final WhatsAppPhoneNumberRepository phoneNumberRepository;

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
    public WhatsAppHealthStatusResponse checkWhatsAppConfigStatus(Company company) {
        
        List<WhatsAppPhoneNumber> phoneNumbers = phoneNumberRepository.findByCompany(company);
        List<ChannelStatus> channelStatuses = new ArrayList<>();

        if (phoneNumbers.isEmpty()) {
            return WhatsAppHealthStatusResponse.builder()
                    .companyId(company.getId())
                    .wabaConfigured(false)
                    .channels(List.of())
                    .build();
        }

        // 3. Verificar status de cada canal na Meta (Real-time Probe)
        for (WhatsAppPhoneNumber phone : phoneNumbers) {
            String currentStatus = phone.getStatus();     // Valor do Banco
            String currentQuality = phone.getQualityRating(); // Valor do Banco

            // Se tiver token, tentamos atualizar com dados frescos da Meta
            if (bspSystemUserAccessToken != null && !bspSystemUserAccessToken.isBlank()) {
                try {
                    // GET /{phone_number_id}?fields=status,quality_rating,display_phone_number
                    String url = graphApiBaseUrl + "/" + phone.getPhoneNumberId() + "?fields=status,quality_rating,display_phone_number";
                    
                    JsonNode response = getBspWebClient().get()
                            .uri(url + "&access_token=" + bspSystemUserAccessToken)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .block(); // Bloqueante pois este método retorna o DTO final e não Mono

                    if (response != null) {
                        // Atualiza variáveis locais
                        if (response.has("status")) currentStatus = response.get("status").asText();
                        if (response.has("quality_rating")) currentQuality = response.get("quality_rating").asText();
                        
                        // Atualiza entidade no banco para manter sincronizado
                        boolean changed = false;
                        if (!currentStatus.equals(phone.getStatus())) {
                            phone.setStatus(currentStatus);
                            changed = true;
                        }
                        if (!currentQuality.equals(phone.getQualityRating())) {
                            phone.setQualityRating(currentQuality);
                            changed = true;
                        }
                        // Opcional: Atualizar display number se mudou na Meta
                        if (response.has("display_phone_number")) {
                            String metaDisplay = response.get("display_phone_number").asText();
                            if (!metaDisplay.equals(phone.getDisplayPhoneNumber())) {
                                phone.setDisplayPhoneNumber(metaDisplay);
                                changed = true;
                            }
                        }

                        if (changed) {
                            phoneNumberRepository.save(phone);
                        }
                    }
                } catch (Exception e) {
                    log.error("Falha ao consultar status na Meta para o telefone {}: {}", phone.getPhoneNumberId(), e.getMessage());
                    // Mantém os dados antigos do banco ou marca como UNKNOWN se for crítico
                    currentStatus = "UNKNOWN"; 
                }
            }

            // 4. Monta o DTO do canal
            channelStatuses.add(ChannelStatus.builder()
                    .id(phone.getId())
                    .phoneNumberId(phone.getPhoneNumberId())
                    .wabaId(phone.getWabaId())
                    .displayPhoneNumber(phone.getDisplayPhoneNumber())
                    .alias(phone.getAlias())
                    .isDefault(phone.isDefault())
                    .status(currentStatus)
                    .qualityRating(currentQuality)
                    .build());
        }

        return WhatsAppHealthStatusResponse.builder()
                .companyId(company.getId())
                .wabaConfigured(true)
                .channels(channelStatuses)
                .build();
    }
}
