package com.br.alchieri.consulting.mensageria.chat.service.impl;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.dto.cart.CartDTO;
import com.br.alchieri.consulting.mensageria.dto.cart.CartItemDTO;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.ConversationState;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentMethod;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
import com.br.alchieri.consulting.mensageria.payment.service.PaymentService;
import com.br.alchieri.consulting.mensageria.service.CartService;
import com.br.alchieri.consulting.mensageria.service.impl.SessionService;

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

    // --- ENTRY POINT: Início do Checkout ---
    
    public void startCheckoutFlow(Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        CartDTO cart = session.getCart();
        if (cart.isEmpty()) {
            sendText(contact, "Seu carrinho está vazio.", channel, systemUser);
            return;
        }

        StringBuilder sb = new StringBuilder("🛒 *Resumo do Pedido:*\n\n");
        for (CartItemDTO item : cart.getItems()) {
            sb.append(String.format("- %dx %s (Total: %s)\n", item.getQuantity(), item.getName(), item.getTotal()));
        }
        sb.append("\n💰 *Total Geral: " + cart.getTotalAmount() + "*\n");
        sb.append("\nDeseja finalizar o pedido? Digite *Sim* para confirmar ou *Não* para cancelar.");

        sendText(contact, sb.toString(), channel, systemUser);
        
        sessionService.updateState(session, ConversationState.CONFIRMING_ORDER);
    }

    // --- STEP 1: Confirmação do Pedido ---

    public void processOrderConfirmation(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        if (input.toLowerCase().contains("sim")) {
            try {
                Order order = cartService.checkout(session, contact, channel);
                
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

    // --- STEP 2: Seleção de Pagamento ---

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

    // --- STEP 3: Espera e Suporte (UX) ---

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
}
