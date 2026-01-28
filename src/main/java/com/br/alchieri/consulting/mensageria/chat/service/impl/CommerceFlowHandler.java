package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest.FlowActionPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.dto.cart.CartDTO;
import com.br.alchieri.consulting.mensageria.dto.cart.CartItemDTO;
import com.br.alchieri.consulting.mensageria.model.Address;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.ConversationState;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentMethod;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
import com.br.alchieri.consulting.mensageria.payment.service.PaymentService;
import com.br.alchieri.consulting.mensageria.service.CartService;
import com.br.alchieri.consulting.mensageria.service.impl.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responsável exclusivamente pelo fluxo de Conversational Commerce:
 * - Checkout
 * - Confirmação de Pedido
 * - Seleção de Pagamento
 * - Espera de Pagamento
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommerceFlowHandler {

    private final WhatsAppCloudApiService whatsAppService;
    private final SessionService sessionService;
    private final CartService cartService;
    private final PaymentService paymentService;

    private final ObjectMapper objectMapper;

    // --- ENTRY POINT: Início do Checkout ---
    
    public void startCheckoutFlow(Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        CartDTO cart = session.getCart();
        if (cart.isEmpty()) {
            sendText(contact, "Seu carrinho está vazio.", channel, systemUser);
            return;
        }

        Address address = session.getTempAddress(); 

        if (address == null) {

            String flowId = contact.getCompany().getCheckoutAddressFlowId();
            
            if (flowId != null && !flowId.isBlank()) {
                sendAddressFlow(contact, channel, systemUser, flowId);
                sessionService.updateState(session, ConversationState.FILLING_ADDRESS);
            } else {
                log.warn("Empresa {} não tem Flow de Endereço configurado. Usando fallback.", contact.getCompany().getName());
                sendText(contact, "Por favor, digite seu endereço completo (Rua, Número, Bairro, Cidade - UF):", channel, systemUser);
            }
        } else {
            showOrderSummary(contact, session, systemUser, channel);
        }
    }

    // --- STEP 0.5: Recebimento dos Dados do Flow ---

    public void processAddressData(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        Address newAddress = null;
        // Cenário A: Resposta do Flow (JSON)
        if (input != null && input.trim().startsWith("{")) {
            try {
                Map<String, Object> data = objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
                
                newAddress = new Address();
                newAddress.setPostalCode(getString(data, "cep"));
                newAddress.setStreet(getString(data, "rua"));
                newAddress.setNumber(getString(data, "numero"));
                newAddress.setComplement(getString(data, "complemento"));
                newAddress.setNeighborhood(getString(data, "bairro"));
                newAddress.setCity(getString(data, "cidade"));
                newAddress.setState(getString(data, "estado"));
                
            } catch (Exception e) {
                log.warn("Falha ao parsear JSON de endereço. Tentando tratar como texto livre. Input: {}", input);
            }
        }

        // Cenário B: Usuário digitou texto livre (Fallback ou Erro no Flow)
        if (newAddress == null && input != null && !input.isBlank() && !input.trim().startsWith("{")) {
            // Aqui você poderia implementar uma lógica simples de regex ou apenas salvar como 'Street'
            // Por simplicidade, salvamos tudo na rua para ajuste manual posterior se necessário
            newAddress = new Address();
            newAddress.setStreet(input); 
        }

        if (newAddress != null) {
            session.setTempAddress(newAddress);
            sessionService.saveSession(session);

            sendText(contact, "📍 Endereço recebido!", channel, systemUser);
            showOrderSummary(contact, session, systemUser, channel);
        } else {
            // Se chegou aqui, não entendemos nada (nem JSON nem texto válido)
            sendText(contact, "Não consegui entender o endereço. Por favor, preencha o formulário ou digite o endereço completo:", channel, systemUser);
            // Mantém no estado FILLING_ADDRESS
        }
    }

    // --- STEP 1: Resumo do Pedido (Refatorado) ---

    private void showOrderSummary(Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        CartDTO cart = session.getCart();
        Address addr = session.getTempAddress();

        StringBuilder sb = new StringBuilder("🛒 *Resumo do Pedido:*\n\n");
        for (CartItemDTO item : cart.getItems()) {
            sb.append(String.format("- %dx %s (Total: %s)\n", item.getQuantity(), item.getName(), item.getTotal()));
        }
        
        sb.append("\n📍 *Entrega em:* " + addr.getStreet() + ", " + addr.getNumber() + " - " + addr.getCity());
        sb.append("\n💰 *Total Geral: " + cart.getTotalAmount() + "*\n");
        sb.append("\nDeseja finalizar o pedido? Digite *Sim* para confirmar ou *Não* para cancelar.");

        sendText(contact, sb.toString(), channel, systemUser);
        sessionService.updateState(session, ConversationState.CONFIRMING_ORDER);
    }

    // --- STEP 2: Confirmação do Pedido ---

    public void processOrderConfirmation(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        if (input.toLowerCase().contains("sim")) {
            try {
                // Recupera o endereço da sessão
                Address deliveryAddr = session.getTempAddress();

                Order order = cartService.checkout(session, contact, channel, deliveryAddr);
                
                // Salva o ID do pedido no contexto da sessão
                session.addContextData("current_order_id", order.getId().toString());

                String msg = "✅ Pedido #" + order.getId() + " gerado com sucesso!\n\n"
                           + "Como deseja pagar?\n"
                           + "1️⃣ Pix (Aprovação Imediata)\n"
                           + "2️⃣ Cartão de Crédito / Link";
                
                sendText(contact, msg, channel, systemUser);
                
                sessionService.updateState(session, ConversationState.SELECTING_PAYMENT_METHOD);
                
            } catch (Exception e) {
                log.error("Erro no checkout", e);
                sendText(contact, "Ocorreu um erro ao processar seu pedido: " + e.getMessage(), channel, systemUser);
            }
        } else if (input.toLowerCase().contains("não") || input.toLowerCase().contains("nao")) {
            cartService.clearCart(session);
            sendText(contact, "Pedido cancelado.", channel, systemUser);
            sessionService.resetSession(session);
        } else {
            sendText(contact, "Por favor, responda com Sim ou Não.", channel, systemUser);
        }
    }

    // --- STEP 3: Seleção de Pagamento ---

    public void processPaymentSelection(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        String orderIdStr = session.getContextData("current_order_id");
        if (orderIdStr == null) {
            sessionService.resetSession(session);
            return;
        }
        Long orderId = Long.parseLong(orderIdStr);
        String option = input.trim();
        Order updatedOrder;

        try {
            if (option.equals("1") || option.toLowerCase().contains("pix")) {
                sendText(contact, "Gerando Pix... aguarde.", channel, systemUser);
                
                updatedOrder = paymentService.generatePayment(orderId, PaymentMethod.PIX);
                
                sendText(contact, "Aqui está seu código Pix Copia e Cola 👇", channel, systemUser);
                sendText(contact, updatedOrder.getPixCopyPaste(), channel, systemUser); // Código puro

                // Contexto para UX (Reenvio)
                session.addContextData("last_pix_code", updatedOrder.getPixCopyPaste());

            } else if (option.equals("2") || option.toLowerCase().contains("cartao") || option.toLowerCase().contains("link")) {
                sendText(contact, "Gerando Link... aguarde.", channel, systemUser);
                
                updatedOrder = paymentService.generatePayment(orderId, PaymentMethod.CREDIT_CARD_LINK);
                
                String linkMsg = "Clique no link abaixo para pagar com Cartão: 👇\n" + updatedOrder.getPaymentUrl();
                sendText(contact, linkMsg, channel, systemUser);

                // Contexto para UX (Reenvio)
                session.addContextData("last_payment_link", updatedOrder.getPaymentUrl());

            } else {
                sendText(contact, "Opção inválida. Digite 1 (Pix) ou 2 (Cartão).", channel, systemUser);
                return; 
            }
            
            // Estado de espera (UX)
            String instructions = "Fico no aguardo da confirmação! \n\n"
                                + "🔄 Se precisar do código novamente, digite *Pix* ou *Link*.\n"
                                + "❌ Para encerrar, digite *Sair*.";
            
            sendText(contact, instructions, channel, systemUser);
            sessionService.updateState(session, ConversationState.WAITING_PAYMENT_CONFIRMATION);

        } catch (Exception e) {
            log.error("Erro ao gerar pagamento", e);
            sendText(contact, "Erro ao gerar pagamento. Tente novamente mais tarde.", channel, systemUser);
            sessionService.resetSession(session);
        }
    }

    // --- STEP 4: Espera e Suporte (UX) ---

    public void processPaymentWait(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        String lowerInput = input.toLowerCase().trim();

        if (lowerInput.contains("pix") || lowerInput.contains("codigo")) {
            String lastPix = session.getContextData("last_pix_code");
            if (lastPix != null) {
                sendText(contact, "Aqui está o código novamente:", channel, systemUser);
                sendText(contact, lastPix, channel, systemUser);
            } else {
                sendText(contact, "Não encontrei um código Pix recente.", channel, systemUser);
            }
        } else if (lowerInput.contains("link") || lowerInput.contains("cartao")) {
            String lastLink = session.getContextData("last_payment_link");
             if (lastLink != null) {
                sendText(contact, "Aqui está o link novamente: " + lastLink, channel, systemUser);
            } else {
                sendText(contact, "Não encontrei um link de pagamento recente.", channel, systemUser);
            }
        } else if (lowerInput.contains("sair") || lowerInput.contains("encerrar")) {
            sendText(contact, "Atendimento encerrado. Obrigado!", channel, systemUser);
            sessionService.resetSession(session);
        } else if (lowerInput.contains("ja paguei") || 
                lowerInput.contains("paguei") || 
                lowerInput.contains("realizei o pagamento") || 
                lowerInput.contains("confirmar")) {
            sendText(contact, "Obrigado! 🚀\n\nAssim que o banco confirmar a transação, você receberá a notificação oficial aqui automaticamente.", channel, systemUser);
        } else {
            sendText(contact, "Ainda aguardando. Digite *Pix* para ver o código ou *Sair* para finalizar.", channel, systemUser);
        }
    }

    private void sendAddressFlow(Contact contact, WhatsAppPhoneNumber channel, User systemUser, String flowId) {
        SendInteractiveFlowMessageRequest req = new SendInteractiveFlowMessageRequest();
        req.setTo(contact.getPhoneNumber());
        req.setFromPhoneNumberId(channel.getPhoneNumberId());
        req.setFlowName("address_collection"); // Nome do fluxo na Meta
        req.setFlowId(flowId);
        req.setBodyText("Para calcular o frete e entregar seu pedido, precisamos do seu endereço. Clique abaixo 👇");
        req.setFlowCta("Preencher Endereço");
        req.setMode("published"); // Use "draft" se estiver testando sem publicar
        req.setFlowAction("navigate");

        FlowActionPayload payload = new FlowActionPayload();
        payload.setScreen("ADDRESS_SCREEN"); // Nome da tela inicial no seu JSON do Flow
        req.setFlowActionPayload(payload);

        whatsAppService.sendInteractiveFlowMessage(req, systemUser).subscribe();
    }

    // Helper privado para simplificar envio de texto
    private void sendText(Contact contact, String msg, WhatsAppPhoneNumber channel, User user) {
        SendTextMessageRequest req = new SendTextMessageRequest();
        req.setTo(contact.getPhoneNumber());
        req.setMessage(msg);
        if (channel != null) {
            req.setFromPhoneNumberId(channel.getPhoneNumberId());
        }
        whatsAppService.sendTextMessage(req, user).subscribe();
    }

    private String getString(Map<String, Object> map, String key) {
        
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : "";
    }
}
