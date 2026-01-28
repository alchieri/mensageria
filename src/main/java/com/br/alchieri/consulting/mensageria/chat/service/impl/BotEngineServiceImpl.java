package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalTime;
import java.util.List;
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
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.enums.ConversationState;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
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
    private final CommerceFlowHandler commerceHandler;

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

        // 1. Prioridade: Preenchimento de Endereço (Checkout)
        if (ConversationState.FILLING_ADDRESS.name().equals(state)) {
            // Se o input for nulo ou vazio (erro de extração), avisa o handler
            if (input == null || input.isBlank()) {
                commerceHandler.processAddressData(null, contact, session, systemUser, channel);
            } else {
                // Passa o JSON (ou texto se o usuario digitou manual)
                commerceHandler.processAddressData(input, contact, session, systemUser, channel);
            }
            return;
        }

        // 2. Prioridade: Estados de Checkout
        if (isCommerceState(state)) {
            dispatchToCommerceHandler(state, input, contact, session, systemUser, channel);
            return;
        }

        // 3. Trigger Explícito
        if ("CHECKOUT_TRIGGER".equals(input)) {
            commerceHandler.startCheckoutFlow(contact, session, systemUser, channel);
            return;
        }

        // 4. Fluxo Padrão (Árvore de Decisão)
        processStandardBotFlow(input, contact, session, systemUser, channel);
    }

    private boolean isCommerceState(String state) {
        
        return ConversationState.CONFIRMING_ORDER.name().equals(state) ||
               ConversationState.SELECTING_PAYMENT_METHOD.name().equals(state) ||
               ConversationState.WAITING_PAYMENT_CONFIRMATION.name().equals(state);
    }

    private void dispatchToCommerceHandler(String state, String input, Contact contact, UserSession session, User user, WhatsAppPhoneNumber channel) {
        
        if (ConversationState.CONFIRMING_ORDER.name().equals(state)) {
            commerceHandler.processOrderConfirmation(input, contact, session, user, channel);
        } else if (ConversationState.SELECTING_PAYMENT_METHOD.name().equals(state)) {
            commerceHandler.processPaymentSelection(input, contact, session, user, channel);
        } else if (ConversationState.WAITING_PAYMENT_CONFIRMATION.name().equals(state)) {
            commerceHandler.processPaymentWait(input, contact, session, user, channel);
        }
    }

    // --- EXECUÇÃO PADRÃO DO BOT ---

    private void processStandardBotFlow(String input, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
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
                sendText(contact, "Transferindo para um atendente...", channel, systemUser);
                executeHandoffStep(currentStep, contact, session, systemUser, channel); 
            } else if (selectedOption.getTargetStep() != null) {
                executeStep(selectedOption.getTargetStep(), contact, session, systemUser, channel);
            } else {
                sessionService.resetSession(session);
            }
        } else {
            sendText(contact, "Opção inválida. Tente novamente.", channel, systemUser);
        }
    }

    private void executeStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
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
                    sendText(contact, "Erro: Tipo de passo não suportado.", channel, systemUser);
                }
            }
        } catch (Exception e) {
            log.error("Erro crítico ao executar passo do bot ID {}: {}", step.getId(), e.getMessage(), e);
            sendText(contact, "Desculpe, ocorreu um erro técnico no bot.", channel, systemUser);
        }
    }

    private void executeTextStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) {
        
        StringBuilder body = new StringBuilder(step.getContent());
        if (step.getOptions() != null && !step.getOptions().isEmpty()) {
            body.append("\n\n");
            step.getOptions().sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
            for (BotOption opt : step.getOptions()) {
                body.append("👉 *").append(opt.getKeyword()).append("* - ").append(opt.getLabel()).append("\n");
            }
        }
        sendText(contact, body.toString(), channel, systemUser);
    }

    private void executeFlowStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) throws JsonProcessingException {
        
        Long flowId = Long.valueOf(step.getContent());
        Flow flow = flowRepository.findById(flowId).orElse(null);
        
        if (flow == null || flow.getStatus() != FlowStatus.PUBLISHED) {
            sendText(contact, "Erro técnico: Fluxo indisponível.", channel, systemUser);
            return;
        }

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

        if (step.getMetadata() != null && !step.getMetadata().isBlank()) {
            JsonNode metaNode = objectMapper.readTree(step.getMetadata());
            if (metaNode.has("header")) request.setHeaderText(metaNode.path("header").asText());
            if (metaNode.has("body")) request.setBodyText(metaNode.path("body").asText());
            if (metaNode.has("footer")) request.setFooterText(metaNode.path("footer").asText());
            if (metaNode.has("cta_label")) request.setFlowCta(metaNode.path("cta_label").asText());
            if (metaNode.has("screen_id")) flowActionPayload.setScreen(metaNode.path("screen_id").asText());
        }
        
        request.setFlowActionPayload(flowActionPayload);
        whatsAppService.sendInteractiveFlowMessage(request, systemUser).subscribe();
    }

    private void executeTemplateStep(BotStep step, Contact contact, User systemUser, WhatsAppPhoneNumber channel) throws JsonProcessingException {
        
        Long templateId = Long.valueOf(step.getContent());
        ClientTemplate template = templateRepository.findById(templateId).orElse(null);
        
        if (template == null || !"APPROVED".equalsIgnoreCase(template.getStatus())) {
            sendText(contact, "Erro técnico: Template indisponível.", channel, systemUser);
            return;
        }

        SendTemplateMessageRequest request = new SendTemplateMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setFromPhoneNumberId(channel.getPhoneNumberId());
        request.setTemplateName(template.getTemplateName());
        request.setLanguageCode(template.getLanguage());

        if (step.getMetadata() != null && !step.getMetadata().isBlank()) {
            JsonNode metaNode = objectMapper.readTree(step.getMetadata());
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
        req.setFromPhoneNumberId(channel.getPhoneNumberId());
        req.setType(type);
        req.setMediaId(mediaId);
        req.setCaption(caption);
        whatsAppService.sendMediaMessage(req, systemUser).subscribe();
    }

    private void executeHandoffStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        String message = step.getContent();
        if (message == null || message.isBlank()) message = "Aguarde um momento.";
        
        sendText(contact, message, channel, systemUser);
        
        sessionService.updateState(session, ConversationState.IN_SERVICE_HUMAN);
        session.setBotActive(false);
        session.setCurrentBotId(null);
        session.setCurrentStepId(null);
        sessionService.saveSession(session);
    }

    private void executeEndStep(BotStep step, Contact contact, UserSession session, User systemUser, WhatsAppPhoneNumber channel) {
        
        if (step.getContent() != null && !step.getContent().isBlank()) {
            sendText(contact, step.getContent(), channel, systemUser);
        }
        sessionService.resetSession(session);
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