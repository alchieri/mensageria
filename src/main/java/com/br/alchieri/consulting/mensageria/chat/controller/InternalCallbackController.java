package com.br.alchieri.consulting.mensageria.chat.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.callback.InternalCallbackPayload;
import com.br.alchieri.consulting.mensageria.service.InternalEventService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/internal-callbacks") // Path para callbacks internos
@Hidden // Esconder do Swagger público
@Slf4j
public class InternalCallbackController {

    // Injetar um API Key do application.properties para segurança
    private final String internalApiKey;
    private final InternalEventService internalEventService;

    public InternalCallbackController(
            @Value("${app.internal.api-key}") String internalApiKey,
            InternalEventService internalEventService) {
        
        if (internalApiKey == null || internalApiKey.isBlank() || 
                internalApiKey.equals("UMA_CHAVE_SECRETA_MUITO_FORTE_E_ALEATORIA_AQUI")) {
            
            log.error("!!!!!!!!!! A CHAVE DE API INTERNA (app.internal.api-key) NÃO ESTÁ CONFIGURADA CORRETAMENTE. ENDPOINT VULNERÁVEL! !!!!!!!!!!");
            throw new IllegalStateException("Chave de API interna não configurada.");
        }
        this.internalApiKey = internalApiKey;
        this.internalEventService = internalEventService;
    }


    @PostMapping("/events")
    public ResponseEntity<Void> receiveInternalEvent(
            @RequestBody InternalCallbackPayload<?> payload, // Usa wildcard para aceitar qualquer tipo de 'data'
            @RequestHeader("X-Internal-Api-Key") String apiKey) {

        // 1. Segurança Simples: Verificar a chave de API interna
        if (!this.internalApiKey.equals(apiKey)) {
            log.warn("Tentativa de chamada ao callback interno com API Key inválida.");
            return ResponseEntity.status(403).build(); // Forbidden
        }

        // 2. Delega para o serviço (Reutilização de código)
        internalEventService.processInternalEvent(payload);

        return ResponseEntity.ok().build();
    }
}
