package com.br.alchieri.consulting.mensageria.chat.consumer;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.br.alchieri.consulting.mensageria.chat.dto.webhook.WebhookEventPayload;
import com.br.alchieri.consulting.mensageria.chat.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookSqsListener {

    private final WebhookService webhookService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SqsListener("${webhook-queue.name}")
    public void processWebhookEvent(@Payload WebhookEventPayload eventPayload) {
        
        log.info("Processing webhook event from SQS queue.");
        
        String payloadJson = eventPayload.getRawPayload();
        String wamid = extractWamid(payloadJson);

        // LÓGICA DE IDEMPOTÊNCIA
        if (wamid != null) {
            // Tenta setar a chave no Redis. Se retornar TRUE, é novo. Se FALSE, já existe.
            // TTL de 24h para garantir que não reprocesse duplicatas do dia.
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent("processed_msg:" + wamid, "1", Duration.ofHours(24));

            if (Boolean.FALSE.equals(isNew)) {
                log.info("Evento duplicado descartado (WAMID: {}).", wamid);
                return; // Descarta silenciosamente (ACK para a fila)
            }
        }

        try {
            webhookService.processWebhookPayload(payloadJson, eventPayload.getSignature());
            log.info("Webhook event processed successfully.");
        } catch (Exception e) {
            log.error("Erro ao processar evento de webhook: {}", e.getMessage(), e);
            // Se falhou, removemos do Redis para permitir retry (opcional, dependendo da estratégia)
            if (wamid != null) {
                redisTemplate.delete("processed_msg:" + wamid);
            }
            throw e; // DLQ handling
        }
    }

    private String extractWamid(String json) {
        
        try {
            JsonNode root = objectMapper.readTree(json);
            
            // Navegação segura para evitar NullPointerException
            JsonNode entryNode = root.path("entry");
            if (entryNode.isArray() && !entryNode.isEmpty()) {
                JsonNode changesNode = entryNode.get(0).path("changes");
                if (changesNode.isArray() && !changesNode.isEmpty()) {
                    JsonNode valueNode = changesNode.get(0).path("value");
                    
                    // Caso 1: Mensagem recebida (messages)
                    JsonNode messagesNode = valueNode.path("messages");
                    if (messagesNode.isArray() && !messagesNode.isEmpty()) {
                        return messagesNode.get(0).path("id").asText(null);
                    }
                    
                    // Caso 2: Atualização de status (statuses) - também tem ID único
                    JsonNode statusesNode = valueNode.path("statuses");
                    if (statusesNode.isArray() && !statusesNode.isEmpty()) {
                        return statusesNode.get(0).path("id").asText(null);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível extrair WAMID do payload: {}", e.getMessage());
        }
        return null; // Retorna null se não encontrar, processa sem verificação de duplicidade
    }
}
