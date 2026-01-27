package com.br.alchieri.consulting.mensageria.payment.gateway.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentProvider;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentStatus;
import com.br.alchieri.consulting.mensageria.payment.dto.PaymentResponseDTO;
import com.br.alchieri.consulting.mensageria.payment.gateway.PaymentGateway;
import com.br.alchieri.consulting.mensageria.payment.model.PaymentConfig;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MercadoPagoGatewayStrategy implements PaymentGateway {

    private final WebClient webClient = WebClient.create("https://api.mercadopago.com");

    @Override
    public PaymentProvider getProviderName() {
        return PaymentProvider.MERCADO_PAGO;
    }

    @Override
    public PaymentResponseDTO createPixPayment(Order order, PaymentConfig config) {
        try {
            // Monta o Payload do Mercado Pago
            Map<String, Object> body = new HashMap<>();
            body.put("transaction_amount", order.getTotalAmount());
            body.put("description", "Pedido #" + order.getId() + " - " + order.getCompany().getName());
            body.put("payment_method_id", "pix");
            
            // Payer Email é obrigatório. Se o Order não tem, usamos um placeholder ou do contato
            // Idealmente, pedir email no fluxo do bot. Aqui usaremos um dummy se nulo.
            Map<String, Object> payer = new HashMap<>();
            payer.put("email", "cliente_" + order.getContact().getPhoneNumber() + "@email.com"); 
            payer.put("first_name", order.getContact().getName());
            body.put("payer", payer);

            // Metadata para identificar no webhook
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("order_id", order.getId());
            metadata.put("company_id", order.getCompany().getId());
            body.put("metadata", metadata);

            // Chamada API
            JsonNode response = webClient.post()
                    .uri("/v1/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(body))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("id")) {
                throw new BusinessException("Erro ao criar Pix no Mercado Pago: Resposta inválida.");
            }

            // Extrair dados do Pix
            String externalId = response.get("id").asText();
            String status = response.get("status").asText();
            
            // Dados do QR Code ficam dentro de point_of_interaction -> transaction_data
            JsonNode txData = response.path("point_of_interaction").path("transaction_data");
            String copyPaste = txData.path("qr_code").asText();
            String qrCodeBase64 = txData.path("qr_code_base64").asText();
            String ticketUrl = response.path("point_of_interaction").path("transaction_data").path("ticket_url").asText();

            return PaymentResponseDTO.builder()
                    .externalId(externalId)
                    .pixCopyPaste(copyPaste)
                    .qrCodeUrl(qrCodeBase64) // MP retorna base64, ou url externa
                    .paymentUrl(ticketUrl) // URL visual do pagamento
                    .status(status)
                    .build();

        } catch (Exception e) {
            log.error("Erro integração Mercado Pago: {}", e.getMessage(), e);
            throw new BusinessException("Falha ao gerar Pix: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponseDTO createPaymentLink(Order order, PaymentConfig config) {
        // Implementação para Preferences (Link de Checkout)
        // Endpoint: POST /checkout/preferences
        // ... (Similar ao Pix, mas retorna init_point)
        // Deixando simples para focar no Pix agora
        throw new BusinessException("Link de Checkout Mercado Pago não implementado neste exemplo.");
    }

    @Override
    public PaymentStatus checkPaymentStatus(String externalId, PaymentConfig config) {
        try {
            // GET https://api.mercadopago.com/v1/payments/{id}
            JsonNode response = webClient.get()
                    .uri("/v1/payments/" + externalId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("status")) {
                log.warn("Resposta inválida ao consultar pagamento MP ID {}", externalId);
                return PaymentStatus.PENDING; // Na dúvida, mantém pendente
            }

            String mpStatus = response.get("status").asText(); // approved, pending, rejected, cancelled...
            
            // Mapeia status do MP para nosso Enum
            return mapMercadoPagoStatus(mpStatus);

        } catch (Exception e) {
            log.error("Erro ao consultar status do pagamento {} no Mercado Pago: {}", externalId, e.getMessage());
            // Em caso de erro de conexão, assumimos que não mudou (ou tratamos como erro)
            return PaymentStatus.PENDING; 
        }
    }

    private PaymentStatus mapMercadoPagoStatus(String mpStatus) {
        switch (mpStatus) {
            case "approved":
                return PaymentStatus.PAID;
            case "pending":
            case "in_process":
            case "authorized":
                return PaymentStatus.PENDING;
            case "rejected":
            case "cancelled":
            case "refunded":
            case "charged_back":
                return PaymentStatus.FAILED; // Ou CANCELED/REFUNDED se tiver esses enums
            default:
                return PaymentStatus.PENDING;
        }
    }
}
