package com.br.alchieri.consulting.mensageria.chat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.service.WebhookService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/webhook/whatsapp") // Path base para o webhook
@Hidden // Esconde este controller da documentação Swagger principal
@Validated // Necessário para validar @RequestParam
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") @NotBlank @Pattern(regexp = "subscribe", message = "hub.mode deve ser 'subscribe'") String mode,
            @RequestParam("hub.verify_token") @NotBlank(message = "hub.verify_token é obrigatório") String token,
            @RequestParam("hub.challenge") @NotBlank(message = "hub.challenge é obrigatório") String challenge) {

        logger.info("Recebida requisição GET de verificação de webhook: mode={}, token={}, challenge={}", mode, token, challenge);

        if (verifyToken != null && verifyToken.equals(token)) {
            logger.info("Token de verificação VÁLIDO. Respondendo com o challenge.");
            return ResponseEntity.ok(challenge);
        } else {
            logger.warn("Falha na verificação do Webhook: Modo ou Token inválidos (Esperado: {}).", verifyToken);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Falha na verificação do token.");
        }
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhookNotification(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        logger.info("Webhook received, queueing for processing.");
        try {
            // O serviço de webhook agora fará a verificação E o enfileiramento
            webhookService.queueWebhookEvent(payload, signature);
            logger.info("Webhook event successfully queued.");
            return ResponseEntity.ok().build(); // Responde 200 OK imediatamente
        } catch (Exception e) {
            logger.error("Failed to queue webhook event. Payload: {}", payload, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
