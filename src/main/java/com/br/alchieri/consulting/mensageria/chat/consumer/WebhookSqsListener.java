package com.br.alchieri.consulting.mensageria.chat.consumer;

import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.br.alchieri.consulting.mensageria.chat.dto.webhook.WebhookEventPayload;
import com.br.alchieri.consulting.mensageria.chat.service.WebhookService;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookSqsListener {

    private final WebhookService webhookService;

    @SqsListener("${webhook-queue.name}")
    public void processWebhookEvent(@Payload WebhookEventPayload eventPayload) {
        log.info("Processing webhook event from SQS queue.");
        try {
            // Chama o método de processamento passando o payload bruto e a assinatura
            webhookService.processWebhookPayload(eventPayload.getRawPayload(), eventPayload.getSignature());
            log.info("Webhook event processed successfully.");
        } catch (Exception e) {
            log.error("Erro ao processar evento de webhook da fila SQS: {}", e.getMessage(), e);
            // Lança a exceção para que a mensagem seja reenviada ou vá para a DLQ
            throw e;
        }
    }
}
