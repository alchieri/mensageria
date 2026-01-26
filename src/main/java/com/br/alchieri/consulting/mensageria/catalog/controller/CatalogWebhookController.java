package com.br.alchieri.consulting.mensageria.catalog.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.catalog.dto.webhook.MetaCatalogEvent;
import com.br.alchieri.consulting.mensageria.catalog.service.CatalogWebhookService;
import com.br.alchieri.consulting.mensageria.util.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/webhooks/catalog")
@RequiredArgsConstructor
@Slf4j
public class CatalogWebhookController {

    private final CatalogWebhookService catalogWebhookService;
    private final SignatureUtil signatureUtil;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.webhook.verify-token}")
    private String verifyToken; // Pode usar o mesmo token do chat ou criar um novo property

    /**
     * VERIFICAÇÃO DO WEBHOOK (GET)
     * A Meta chama isso quando você cadastra a URL no painel.
     */
    @GetMapping
    @Hidden
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        log.info("Recebendo verificação de webhook de Catálogo...");

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook de Catálogo verificado com sucesso!");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("Falha na verificação do webhook de Catálogo. Token inválido.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * RECEBIMENTO DE EVENTOS (POST)
     * A Meta envia atualizações de produtos aqui.
     */
    @PostMapping
    public ResponseEntity<Void> receiveCatalogEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        // 1. Validar Assinatura (Segurança)
        // Nota: Para catálogos, a chave de assinatura pode ser o App Secret. 
        if (signature != null) {
             signatureUtil.verifySignature(payload, signature);
        }

        try {
            // 2. Parse do JSON
            MetaCatalogEvent event = objectMapper.readValue(payload, MetaCatalogEvent.class);
            
            // 3. Processar (idealmente async, mas para catalogo o volume costuma ser menor)
            if ("product_item".equals(event.getObject())) {
                catalogWebhookService.processCatalogEvent(event);
            } else {
                log.debug("Evento ignorado (objeto desconhecido): {}", event.getObject());
            }

        } catch (Exception e) {
            log.error("Erro ao processar webhook de catálogo: {}", e.getMessage(), e);
            // Retornamos 200 OK mesmo com erro interno para a Meta não ficar reenviando infinitamente
            // a menos que queiramos retry.
        }

        return ResponseEntity.ok().build();
    }
}
