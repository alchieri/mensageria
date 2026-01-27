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
            @RequestBody JsonNode payload, 
            @RequestParam(value = "id", required = false) String queryId,
            @RequestParam(value = "topic", required = false) String topic) {
        
        log.info("Webhook Mercado Pago recebido. QueryID: {}, Topic: {}, Payload: {}", queryId, topic, payload);

        String paymentId = null;

        // 1. Tenta extrair ID da Query String (formato comum para notificações IPN)
        if (queryId != null) {
            paymentId = queryId;
        } 
        // 2. Tenta extrair do JSON (formato Webhook V2)
        else if (payload != null && payload.has("data") && payload.get("data").has("id")) {
            paymentId = payload.get("data").get("id").asText();
        }

        // Se achou um ID e é um tópico de pagamento
        if (paymentId != null) {
             // Filtra tópicos irrelevantes (merchant_order, etc) se quiser ser estrito
             // Mas geralmente checar o status mal não faz.
             
             try {
                 // AQUI CHAMAMOS O NOVO MÉTODO DE SEGURANÇA
                 paymentService.syncStatusWithProvider(paymentId);
             } catch (Exception e) {
                 log.error("Erro ao processar webhook MP ID {}: {}", paymentId, e.getMessage());
                 // Retornamos 200 OK mesmo com erro interno para o MP parar de reenviar (ou 500 se quiser retry)
                 // Geralmente 200 é mais seguro para evitar loops se for erro de lógica nossa.
             }
        }

        return ResponseEntity.ok().build();
    }
}
