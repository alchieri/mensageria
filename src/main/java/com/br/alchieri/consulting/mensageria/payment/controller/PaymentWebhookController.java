package com.br.alchieri.consulting.mensageria.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.payment.service.PaymentService;
import com.br.alchieri.consulting.mensageria.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/webhooks/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final PaymentService paymentService;

    private final OrderRepository orderRepository;

    // Endpoint genérico (Adapte para o JSON específico do seu Gateway)
    @PostMapping("/generic")
    public ResponseEntity<Void> handlePaymentNotification(@RequestBody JsonNode payload, 
                                                          @RequestHeader(value = "X-Signature", required = false) String signature) {
        log.info("Recebido webhook de pagamento: {}", payload);

        // Exemplo Genérico: {"event": "PAYMENT_RECEIVED", "payment_id": "12345"}
        String event = payload.path("event").asText();
        String externalId = payload.path("payment_id").asText();

        if ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event)) {
            
            // 1. Atualiza Status
            paymentService.confirmPayment(externalId, "confirmed");
            
            // 2. Notifica Cliente no WhatsApp
            // Precisamos recuperar o Order para saber quem é o cliente e qual canal usar
            orderRepository.findByExternalPaymentId(externalId).ifPresent(order -> {
                String msg = "🎉 Pagamento do pedido #" + order.getId() + " confirmado! Estamos preparando seu envio.";
                
                // Precisamos recuperar o System User para enviar (pode ser um usuário de sistema genérico ou o dono da empresa)
                // Por simplificação, aqui você injetaria um User Service ou buscaria o admin da company do order.
                // User systemUser = ... 
                
                // whatsAppService.sendTextMessage(..., systemUser).subscribe();
                log.info("Pagamento confirmado para Pedido {}", order.getId());
            });
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> handleMercadoPagoWebhook(
            @RequestBody(required = false) JsonNode payload, 
            @RequestParam(value = "id", required = false) String queryId,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "type", required = false) String type) {
        
        // Log para debug (cuidado com dados sensíveis em produção)
        log.info("Webhook MP Recebido. QueryId: {}, Topic: {}, Type: {}", queryId, topic, type);

        String paymentId = null;

        // Estratégia de Extração do ID do Pagamento (MP envia de várias formas)
        if (queryId != null) {
            paymentId = queryId;
        } else if (payload != null && payload.has("data") && payload.get("data").has("id")) {
            paymentId = payload.get("data").get("id").asText();
        }

        // Validamos se é um evento de pagamento
        boolean isPaymentEvent = "payment".equals(topic) || "payment".equals(type);

        if (paymentId != null && isPaymentEvent) {
             try {
                 // Sincroniza sem precisar de sessão. O método busca o Order pelo ID do pagamento.
                 // Nota: Se o MP mandar apenas o ID do Pagamento (e não a external_reference nossa),
                 // precisamos primeiro consultar a API do MP para descobrir qual é a nossa 'external_reference'.
                 // O método 'syncStatusWithProvider' abaixo deve fazer isso.
                 
                 paymentService.syncStatusWithProvider(paymentId);
                 
             } catch (Exception e) {
                 log.error("Erro processando webhook MP ID {}: {}", paymentId, e.getMessage());
                 // Retornar 200 evita loops infinitos de retry do MP em caso de erro lógico nosso
                 return ResponseEntity.ok().build();
             }
        }

        return ResponseEntity.ok().build();
    }
}
