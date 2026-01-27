package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMediaMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateComponentRequest;
import com.br.alchieri.consulting.mensageria.chat.model.Bot;
import com.br.alchieri.consulting.mensageria.chat.model.BotOption;
import com.br.alchieri.consulting.mensageria.chat.model.BotStep;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.chat.repository.BotRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.BotStepRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ClientTemplateRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.service.BotEngineService;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.dto.cart.CartDTO;
import com.br.alchieri.consulting.mensageria.dto.cart.CartItemDTO;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.cart.Order;
import com.br.alchieri.consulting.mensageria.model.enums.ConversationState;
import com.br.alchieri.consulting.mensageria.model.enums.PaymentMethod;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
import com.br.alchieri.consulting.mensageria.payment.service.PaymentService;
import com.br.alchieri.consulting.mensageria.service.impl.CartServiceImpl;
import com.br.alchieri.consulting.mensageria.service.impl.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotEngineServiceImpl implements BotEngineService {

    private final BotRepository botRepository;
    private final BotStepRepository botStepRepository;
    private final FlowRepository flowRepository;
    private final ClientTemplateRepository templateRepository;

    private final WhatsAppCloudApiService whatsAppService;
    private final SessionService sessionService;
    private final CartServiceImpl cartService;
    private final PaymentService paymentService;

    private final ObjectMapper objectMapper;

    @Override
    public boolean tryTriggerBot(Company company, Contact contact, UserSession session, User systemUser, 
            WhatsAppPhoneNumber channel) {
        
        List<Bot> bots = botRepository.findByCompanyAndIsActiveTrue(company);

        for (Bot bot : bots) {
            if (shouldTrigger(bot)) {
                log.info("Iniciando Bot '{}' para {} via canal {}", bot.getName(), contact.getPhoneNumber(), channel.getPhoneNumberId());
                session.setBotActive(true);
                session.setCurrentBotId(bot.getId());
                executeStep(bot.getRootStep(), contact, session, systemUser, channel);
                return true;
            }
        }
        return false;
    }

    @Override
    public void processInput(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        String state = session.getCurrentState();

        if (ConversationState.CONFIRMING_ORDER.name().equals(state)) {
            handleOrderConfirmation(input, contact, session, systemUser, channel);
            return;
        } else if (ConversationState.SELECTING_PAYMENT_METHOD.name().equals(state)) {
            handlePaymentSelection(input, contact, session, systemUser, channel);
            return;
        } else if (ConversationState.WAITING_PAYMENT_CONFIRMATION.name().equals(state)) {
            handlePaymentConfirmationWait(input, contact, session, systemUser, channel);
            return;
        }

        if ("CHECKOUT_TRIGGER".equals(input)) {
            handleCheckoutTrigger(contact, session, systemUser, channel);
            return;
        }

        Long stepId = session.getCurrentStepId();
        BotStep currentStep = botStepRepository.findById(stepId).orElse(null);

        if (currentStep == null) {
            sessionService.resetSession(session);
            return;
        }

        Optional<BotOption> match = currentStep.getOptions().stream()
                .filter(opt -> checkMatch(input, opt.getKeyword()))
                .findFirst();

        if (match.isPresent()) {
            BotOption selectedOption = match.get();
            
            if (selectedOption.isHandoff()) {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Transferindo para um atendente...", channel), systemUser).subscribe();
                executeHandoffStep(currentStep, contact, session, systemUser, channel); 
            } else if (selectedOption.getTargetStep() != null) {
                executeStep(selectedOption.getTargetStep(), contact, session, systemUser, channel);
            } else {
                sessionService.resetSession(session);
            }
        } else {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Opção inválida. Tente novamente.", channel), systemUser).subscribe();
        }
    }

    // --- EXECUÇÃO DE PASSOS ---

    private void executeStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        log.info("Executando passo bot: ID={}, Tipo={}, Contato={}", step.getId(), step.getStepType(), contact.getPhoneNumber());

        session.setCurrentStepId(step.getId());
        sessionService.saveSession(session);

        try {
            switch (step.getStepType()) {
                case TEXT -> executeTextStep(step, contact, systemUser, channel);
                case FLOW -> executeFlowStep(step, contact, systemUser, channel);
                case TEMPLATE -> executeTemplateStep(step, contact, systemUser, channel);
                case MEDIA -> executeMediaStep(step, contact, systemUser, channel);
                case HANDOFF -> executeHandoffStep(step, contact, session, systemUser, channel);
                case END -> executeEndStep(step, contact, session, systemUser, channel);
                default -> {
                    log.warn("Tipo de passo desconhecido: {}", step.getStepType());
                    whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro: Tipo de passo não suportado.", channel), systemUser).subscribe();
                }
            }
        } catch (Exception e) {
            log.error("Erro crítico ao executar passo do bot ID {}: {}", step.getId(), e.getMessage(), e);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Desculpe, ocorreu um erro técnico no bot.", channel), systemUser).subscribe();
        }
    }

    private void executeTextStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) {
        
        StringBuilder body = new StringBuilder(step.getContent());

        if (step.getOptions() != null && !step.getOptions().isEmpty()) {
            body.append("\n\n");
            step.getOptions().sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

            for (BotOption opt : step.getOptions()) {
                String displayKey = opt.getKeyword();
                body.append("👉 *").append(displayKey).append("* - ").append(opt.getLabel()).append("\n");
            }
        }

        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), body.toString(), channel), systemUser).subscribe();
    }

    private void executeFlowStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) throws JsonProcessingException {
        
        Long flowId = Long.valueOf(step.getContent());
        Flow flow = flowRepository.findById(flowId).orElse(null);
        
        if (flow == null || flow.getStatus() != FlowStatus.PUBLISHED) {
            log.error("Flow ID {} não encontrado ou não publicado.", flowId);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro técnico: Fluxo indisponível.", channel), systemUser).subscribe();
            return;
        }

        String metadataJson = step.getMetadata();
        
        SendInteractiveFlowMessageRequest request = new SendInteractiveFlowMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setFromPhoneNumberId(channel.getPhoneNumberId());
        request.setFlowName(flow.getName());
        request.setFlowToken("BOT_STEP_" + step.getId());
        
        request.setFlowAction("navigate");
        request.setMode("published");
        request.setBodyText("Por favor, preencha os dados abaixo.");
        request.setFlowCta("Abrir");

        SendInteractiveFlowMessageRequest.FlowActionPayload flowActionPayload = new SendInteractiveFlowMessageRequest.FlowActionPayload();
        flowActionPayload.setScreen("SUCCESS");

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            if (metaNode.has("header")) request.setHeaderText(metaNode.path("header").asText());
            if (metaNode.has("body")) request.setBodyText(metaNode.path("body").asText());
            if (metaNode.has("footer")) request.setFooterText(metaNode.path("footer").asText());
            if (metaNode.has("cta_label")) request.setFlowCta(metaNode.path("cta_label").asText());
            if (metaNode.has("screen_id")) flowActionPayload.setScreen(metaNode.path("screen_id").asText());
            if (metaNode.has("data")) {
                 Map<String, Object> dataMap = objectMapper.convertValue(metaNode.path("data"), new TypeReference<Map<String, Object>>() {});
                 flowActionPayload.setData(dataMap);
            }
        }
        
        request.setFlowActionPayload(flowActionPayload);
        whatsAppService.sendInteractiveFlowMessage(request, systemUser).subscribe();
    }

    private void executeTemplateStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) throws JsonProcessingException {
        
        Long templateId = Long.valueOf(step.getContent());
        ClientTemplate template = templateRepository.findById(templateId).orElse(null);
        
        if (template == null || !"APPROVED".equalsIgnoreCase(template.getStatus())) {
            log.error("Template ID {} inválido.", templateId);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro técnico: Template indisponível.", channel), systemUser).subscribe();
            return;
        }

        String metadataJson = step.getMetadata();

        SendTemplateMessageRequest request = new SendTemplateMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setFromPhoneNumberId(channel.getPhoneNumberId()); // IMPORTANTE
        request.setTemplateName(template.getTemplateName());
        request.setLanguageCode(template.getLanguage());

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            if (metaNode.has("language")) {
                request.setLanguageCode(metaNode.path("language").asText());
            }
            if (metaNode.has("components")) {
                List<TemplateComponentRequest> components = objectMapper.convertValue(
                    metaNode.get("components"),
                    new TypeReference<List<TemplateComponentRequest>>() {}
                );
                request.setResolvedComponents(components);
            }
        }

        whatsAppService.sendTemplateMessage(request, systemUser, null).subscribe();
    }

    private void executeMediaStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) throws JsonProcessingException {
        
        String mediaId = step.getContent();
        String type = "image"; 
        String caption = null;

        if (step.getMetadata() != null) {
            JsonNode node = objectMapper.readTree(step.getMetadata());
            if (node.has("caption")) caption = node.path("caption").asText();
            if (node.has("type")) type = node.path("type").asText();
        }
        
        SendMediaMessageRequest req = new SendMediaMessageRequest();
        req.setTo(contact.getPhoneNumber());
        req.setFromPhoneNumberId(channel.getPhoneNumberId()); // IMPORTANTE
        req.setType(type);
        req.setMediaId(mediaId);
        req.setCaption(caption);
        
        whatsAppService.sendMediaMessage(req, systemUser).subscribe();
    }

    private void executeHandoffStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        String message = step.getContent();
        if (message == null || message.isBlank()) {
            message = "Aguarde um momento, estamos transferindo para um atendente humano.";
        }
        
        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message, channel), systemUser).subscribe();
        
        sessionService.updateState(session, ConversationState.IN_SERVICE_HUMAN);
        session.setBotActive(false);
        session.setCurrentBotId(null);
        session.setCurrentStepId(null);
        sessionService.saveSession(session);
        log.info("Transbordo realizado para contato {}", contact.getPhoneNumber());
    }

    private void executeEndStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        String message = step.getContent();
        if (message != null && !message.isBlank()) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message, channel), systemUser).subscribe();
        }
        sessionService.resetSession(session);
    }

    // --- CHECKOUT / CARRINHO ---

    private void handleCheckoutTrigger(Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        CartDTO cart = session.getCart();
        if (cart.isEmpty()) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Seu carrinho está vazio.", channel), systemUser).subscribe();
            return;
        }

        StringBuilder sb = new StringBuilder("🛒 *Resumo do Pedido:*\n\n");
        for (CartItemDTO item : cart.getItems()) {
            sb.append(String.format("- %dx %s (Total: %s)\n", item.getQuantity(), item.getName(), item.getTotal()));
        }
        sb.append("\n💰 *Total Geral: " + cart.getTotalAmount() + "*\n");
        sb.append("\nDeseja finalizar o pedido? Digite *Sim* para confirmar ou *Não* para cancelar.");

        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), sb.toString(), channel), systemUser).subscribe();
        
        sessionService.updateState(session, ConversationState.CONFIRMING_ORDER);
    }

    private void handleOrderConfirmation(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        if (input.toLowerCase().contains("sim")) {
            try {
                Order order = cartService.checkout(session, contact, channel);
                
                session.addContextData("current_order_id", order.getId().toString());

                String msg = "✅ Pedido #" + order.getId() + " gerado!\n\n"
                           + "Como deseja pagar?\n"
                           + "1️⃣ Pix (Aprovação Imediata)\n"
                           + "2️⃣ Cartão de Crédito / Link";
                
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), msg, channel), systemUser).subscribe();
                
                sessionService.updateState(session, ConversationState.SELECTING_PAYMENT_METHOD);
                
            } catch (Exception e) {
                log.error("Erro no checkout", e);
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Ocorreu um erro ao processar seu pedido. Tente novamente.", channel), systemUser).subscribe();
            }
        } else if (input.toLowerCase().contains("não") || input.toLowerCase().contains("nao")) {
            session.getCart().clear();
            sessionService.saveSession(session);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Pedido cancelado.", channel), systemUser).subscribe();
            sessionService.resetSession(session);
        } else {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Por favor, responda com Sim ou Não.", channel), systemUser).subscribe();
        }
    }

    private void handlePaymentSelection(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        String orderIdStr = session.getContextData("current_order_id");
        if (orderIdStr == null) {
            sessionService.resetSession(session);
            return;
        }
        Long orderId = Long.parseLong(orderIdStr);

        String option = input.trim();
        Order updatedOrder = null;

        try {
            if (option.equals("1") || option.toLowerCase().contains("pix")) {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Gerando Pix... aguarde.", channel), systemUser).subscribe();
                
                updatedOrder = paymentService.generatePayment(orderId, PaymentMethod.PIX);
                
                String pixMsg = "Aqui está seu código Pix Copia e Cola 👇";
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), pixMsg, channel), systemUser).subscribe();
                
                // Manda o código puro em outra mensagem
                String pixCode = updatedOrder.getPixCopyPaste();
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), pixCode, channel), systemUser).subscribe();

                // [UX] Salva o código na sessão para reenvio se necessário
                session.addContextData("last_pix_code", pixCode);

            } else if (option.equals("2") || option.toLowerCase().contains("cartao") || option.toLowerCase().contains("link")) {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Gerando Link... aguarde.", channel), systemUser).subscribe();
                
                updatedOrder = paymentService.generatePayment(orderId, PaymentMethod.CREDIT_CARD_LINK);
                
                String paymentUrl = updatedOrder.getPaymentUrl();
                String linkMsg = "Clique no link abaixo para pagar com Cartão: 👇\n" + paymentUrl;
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), linkMsg, channel), systemUser).subscribe();

                // [UX] Salva o link na sessão
                session.addContextData("last_payment_link", paymentUrl);

            } else {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Opção inválida. Digite 1 (Pix) ou 2 (Cartão).", channel), systemUser).subscribe();
                return; 
            }
            
            // [UX IMPROVEMENT] Não reseta a sessão imediatamente. Entra em espera.
            String instructions = "Fico no aguardo da confirmação! \n\n"
                                + "🔄 Se precisar do código novamente, digite *Pix* ou *Link*.\n"
                                + "❌ Para encerrar o atendimento, digite *Sair*.";
            
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), instructions, channel), systemUser).subscribe();
            
            sessionService.updateState(session, ConversationState.WAITING_PAYMENT_CONFIRMATION);

        } catch (Exception e) {
            log.error("Erro ao gerar pagamento", e);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro ao gerar pagamento. Tente novamente mais tarde.", channel), systemUser).subscribe();
            sessionService.resetSession(session);
        }
    }

    private void handlePaymentConfirmationWait(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        String lowerInput = input.toLowerCase().trim();

        if (lowerInput.contains("pix") || lowerInput.contains("codigo") || lowerInput.contains("copia")) {
            String lastPix = session.getContextData("last_pix_code");
            if (lastPix != null) {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Aqui está o código novamente:", channel), systemUser).subscribe();
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), lastPix, channel), systemUser).subscribe();
            } else {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Não encontrei um código Pix recente. Tente gerar novamente.", channel), systemUser).subscribe();
            }
        } else if (lowerInput.contains("link") || lowerInput.contains("cartao") || lowerInput.contains("pagar")) {
            String lastLink = session.getContextData("last_payment_link");
             if (lastLink != null) {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Aqui está o link novamente: " + lastLink, channel), systemUser).subscribe();
            } else {
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Não encontrei um link de pagamento recente.", channel), systemUser).subscribe();
            }
        } else if (lowerInput.contains("sair") || lowerInput.contains("cancelar") || lowerInput.contains("encerrar")) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Atendimento encerrado. Obrigado e volte sempre!", channel), systemUser).subscribe();
            sessionService.resetSession(session);
        } else if (lowerInput.contains("já paguei") || lowerInput.contains("ja paguei") || lowerInput.contains("confirmar")) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Obrigado! Assim que o sistema bancário confirmar, você receberá a notificação aqui automaticamente.", channel), systemUser).subscribe();
        } else {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Ainda aguardando o pagamento. Digite *Pix* para ver o código ou *Sair* para finalizar.", channel), systemUser).subscribe();
        }
    }

    // --- HELPERS ---

    private boolean shouldTrigger(Bot bot) {
        
        if (bot.getTriggerType() == BotTriggerType.ALWAYS) return true;
        
        if (bot.getTriggerType() == BotTriggerType.RANGE_HOURS) {
            LocalTime now = LocalTime.now(); 
            return now.isAfter(bot.getStartTime()) && now.isBefore(bot.getEndTime());
        }
        return false;
    }

    private boolean checkMatch(String input, String keyword) {
        
        if (input == null || keyword == null) return false;
        return input.trim().equalsIgnoreCase(keyword.trim());
    }

    private SendTextMessageRequest createReq(String to, String body, WhatsAppPhoneNumber channel) {
        
        SendTextMessageRequest req = new SendTextMessageRequest();
        req.setTo(to);
        req.setMessage(body);
        if (channel != null) {
            req.setFromPhoneNumberId(channel.getPhoneNumberId());
        }
        return req;
    }
}