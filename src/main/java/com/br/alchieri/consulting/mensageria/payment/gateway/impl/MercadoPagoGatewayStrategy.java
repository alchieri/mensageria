package com.br.alchieri.consulting.mensageria.payment.gateway.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.cart.OrderItem;
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

        }catch (WebClientResponseException e) {
            log.error("Erro HTTP Mercado Pago (Create Pix): Status={} Body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("Erro na operadora de pagamento: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Erro integração Mercado Pago: {}", e.getMessage(), e);
            throw new BusinessException("Falha ao gerar Pix: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponseDTO createPaymentLink(Order order, PaymentConfig config) {
        try {
            // 1. Construção da Preferência
            Map<String, Object> preference = new HashMap<>();
            
            // Itens do Pedido
            List<Map<String, Object>> items = new ArrayList<>();
            for (OrderItem orderItem : order.getItems()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", orderItem.getProductSku());
                item.put("title", orderItem.getProductName());
                item.put("quantity", orderItem.getQuantity());
                item.put("currency_id", "BRL");
                item.put("unit_price", orderItem.getUnitPrice());
                items.add(item);
            }
            preference.put("items", items);

            // Payer (Comprador)
            Map<String, Object> payer = new HashMap<>();
            payer.put("email", "cliente_" + order.getContact().getPhoneNumber() + "@email.com");
            payer.put("name", order.getContact().getName());
            preference.put("payer", payer);

            // Back URLs (Para onde o utilizador volta após pagar)
            String botPhoneNumber = order.getChannel().getDisplayPhoneNumber().replaceAll("[^0-9]", "");
            String waBaseUrl = "https://wa.me/" + botPhoneNumber;

            Map<String, Object> backUrls = new HashMap<>();

            // Sucesso: Redireciona para o WhatsApp com texto "Já paguei"
            backUrls.put("success", waBaseUrl + "?text=Realizei%20o%20pagamento!%20Pode%20confirmar%20o%20pedido%20" + order.getId() + "?");
            // Falha: Redireciona para o WhatsApp relatando problema
            backUrls.put("failure", waBaseUrl + "?text=Tive%20problemas%20com%20o%20pagamento%20do%20pedido%20" + order.getId());
            // Pendente: Redireciona avisando que está em análise
            backUrls.put("pending", waBaseUrl + "?text=O%20pagamento%20do%20pedido%20" + order.getId() + "%20está%20pendente.");
            
            preference.put("back_urls", backUrls);
            // 'approved': Tenta redirecionar automaticamente para o app do WhatsApp assim que o pagamento é aprovado.
            preference.put("auto_return", "approved");

            // External Reference (Crucial para Webhook: ID do Pedido Local)
            preference.put("external_reference", order.getId().toString());
            
            // Metadata adicional
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("company_id", order.getCompany().getId());
            metadata.put("channel_id", order.getChannel().getId());
            preference.put("metadata", metadata);

            // 2. Chamada à API /checkout/preferences
            JsonNode response = webClient.post()
                    .uri("/checkout/preferences")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(preference))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("id")) {
                throw new BusinessException("Erro ao criar Preferência Checkout Pro.");
            }

            // 3. Extração do Link (init_point)
            String preferenceId = response.get("id").asText();
            // init_point = Produção, sandbox_init_point = Testes
            // O Mercado Pago decide qual entregar baseada no Token (Prod vs Test), mas podemos forçar a lógica se necessário.
            String paymentUrl = response.get("init_point").asText(); 
            
            if (response.has("sandbox_init_point") && config.getAccessToken().startsWith("TEST-")) {
                paymentUrl = response.get("sandbox_init_point").asText();
            }

            return PaymentResponseDTO.builder()
                    .externalId(preferenceId) // Aqui guardamos o ID da Preferência
                    .paymentUrl(paymentUrl)   // O link para enviar ao utilizador no WhatsApp
                    .status("CREATED")
                    .build();

        } catch (WebClientResponseException e) {
            log.error("Erro HTTP MP Checkout Pro: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("Erro ao gerar link de pagamento: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Erro genérico MP Checkout Pro", e);
            throw new BusinessException("Falha interna ao processar pagamento.");
        }
    }

    @Override
    public PaymentStatus checkPaymentStatus(String externalId, PaymentConfig config) {
        
        try {
            // Nota: Para Checkout Pro, o 'externalId' salvo inicialmente é o ID da PREFERÊNCIA.
            // Porém, a verificação de status geralmente é feita pelo ID do PAGAMENTO (Payment ID) que vem via Webhook.
            // Se tentarmos consultar GET /v1/payments/{preferenceId}, vai falhar.
            
            // Se o externalId parecer um UUID (formato comum de preferência), não podemos consultar status de pagamento diretamente.
            // A consulta direta só funciona se tivermos o Payment ID (numérico longo).
            if (externalId.contains("-") && externalId.length() > 20) {
                 // É provável que seja um Preference ID.
                 // Não conseguimos saber se foi pago apenas consultando a preferência.
                 // Dependemos do Webhook para atualizar o ID do pedido com o Payment ID real.
                 log.debug("Consultando status por Preference ID não suportado diretamente. Aguardando Webhook.");
                 return PaymentStatus.PENDING;
            }

            // Se for numérico, assume-se que é um Payment ID (provavelmente vindo do Pix ou atualizado via Webhook)
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

            String mpStatus = response.get("status").asText();
            
            return mapMercadoPagoStatus(mpStatus);

        }catch (WebClientResponseException e) {
            
            // 404: O ID do pagamento não existe na Meta. Isso nunca vai mudar.
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.error("Pagamento não encontrado no Mercado Pago (ID: {}). Marcando como FALHA para encerrar.", externalId);
                return PaymentStatus.FAILED; 
            }
            
            // 401/403: Token inválido ou sem permissão. Não adianta tentar de novo.
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("Erro de Autenticação Mercado Pago (Token inválido?). Encerrando verificação.", e);
                return PaymentStatus.FAILED; 
            }

            // Outros 4xx: Erro na requisição (Bad Request).
            if (e.getStatusCode().is4xxClientError()) {
                log.error("Erro cliente Mercado Pago: {}", e.getStatusCode());
                return PaymentStatus.FAILED;
            }

            // 5xx: Erro no Servidor do MP. Isso é transiente.
            log.warn("Mercado Pago indisponível (Status {}). Tentaremos novamente.", e.getStatusCode());
            return PaymentStatus.PENDING;

        } catch (Exception e) {
            // Timeout, DNS, Connection Refused -> Transiente
            log.warn("Erro de conexão ao consultar Mercado Pago (ID: {}): {}", externalId, e.getMessage());
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
            case "in_mediation":
                return PaymentStatus.PENDING;
            case "rejected":
            case "cancelled":
            case "refunded":
            case "charged_back":
                return PaymentStatus.FAILED;
            default:
                log.warn("Status desconhecido do Mercado Pago: {}", mpStatus);
                return PaymentStatus.PENDING;
        }
    }
}
