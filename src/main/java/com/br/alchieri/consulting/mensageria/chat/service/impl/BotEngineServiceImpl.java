package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalTime;
import java.util.HashMap;
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
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;
import com.br.alchieri.consulting.mensageria.chat.repository.BotRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.BotStepRepository;
import com.br.alchieri.consulting.mensageria.chat.service.BotEngineService;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
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

    private final WhatsAppCloudApiService whatsAppService;
    private final SessionService sessionService;

    private final ObjectMapper objectMapper;

    @Override
    public boolean tryTriggerBot(Company company, Contact contact, UserSession session, User systemUser) {
        List<Bot> bots = botRepository.findByCompanyAndIsActiveTrue(company);

        for (Bot bot : bots) {
            if (shouldTrigger(bot)) {
                log.info("Iniciando Bot '{}' para {}", bot.getName(), contact.getPhoneNumber());
                session.setBotActive(true);
                session.setCurrentBotId(bot.getId());
                executeStep(bot.getRootStep(), contact, session, systemUser);
                return true;
            }
        }
        return false;
    }

    @Override
    public void processInput(String input, Contact contact, UserSession session, User systemUser) {
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
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Transferindo para um atendente..."), systemUser).subscribe();
                executeHandoffStep(currentStep, contact, session, systemUser); 
            } else if (selectedOption.getTargetStep() != null) {
                executeStep(selectedOption.getTargetStep(), contact, session, systemUser);
            } else {
                sessionService.resetSession(session);
            }
        } else {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Opção inválida. Tente novamente."), systemUser).subscribe();
        }
    }

    private void executeStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        log.info("Executando passo bot: ID={}, Tipo={}, Contato={}", step.getId(), step.getStepType(), contact.getPhoneNumber());

        session.setCurrentStepId(step.getId());
        sessionService.saveSession(session);

        try {
            switch (step.getStepType()) {
                case TEXT -> executeTextStep(step, contact, systemUser);
                case FLOW -> executeFlowStep(step, contact, systemUser);
                case TEMPLATE -> executeTemplateStep(step, contact, systemUser);
                case MEDIA -> executeMediaStep(step, contact, systemUser);
                case HANDOFF -> executeHandoffStep(step, contact, session, systemUser);
                case END -> executeEndStep(step, contact, session, systemUser);
                default -> {
                    log.warn("Tipo de passo desconhecido: {}", step.getStepType());
                    whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro: Tipo de passo não suportado."), systemUser).subscribe();
                }
            }
        } catch (Exception e) {
            log.error("Erro crítico ao executar passo do bot ID {}: {}", step.getId(), e.getMessage(), e);
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Desculpe, ocorreu um erro técnico no bot."), systemUser).subscribe();
        }
    }

    private void executeTextStep(BotStep step, Contact contact, User systemUser) {
        StringBuilder body = new StringBuilder(step.getContent());

        if (step.getOptions() != null && !step.getOptions().isEmpty()) {
            body.append("\n\n");
            step.getOptions().sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

            for (BotOption opt : step.getOptions()) {
                String displayKey = opt.getKeyword();
                body.append("👉 *").append(displayKey).append("* - ").append(opt.getLabel()).append("\n");
            }
        }

        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), body.toString()), systemUser).subscribe();
    }

    // --- CORREÇÃO 1: FLOW STEP ---
    private void executeFlowStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        
        String flowIdentifier = step.getContent(); // ID ou Nome do Flow
        String metadataJson = step.getMetadata();
        
        SendInteractiveFlowMessageRequest request = new SendInteractiveFlowMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setFlowName(flowIdentifier);
        request.setFlowToken("BOT_STEP_" + step.getId());
        
        // Configuração padrão
        request.setFlowAction("navigate");
        request.setMode("published");
        request.setBodyText("Por favor, continue o atendimento no formulário abaixo.");
        request.setFlowCta("Abrir");

        SendInteractiveFlowMessageRequest.FlowActionPayload flowActionPayload = new SendInteractiveFlowMessageRequest.FlowActionPayload();
        flowActionPayload.setScreen("SUCCESS"); // Tela padrão (fallback)

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            
            if (metaNode.has("header")) request.setHeaderText(metaNode.path("header").asText());
            if (metaNode.has("body")) request.setBodyText(metaNode.path("body").asText());
            if (metaNode.has("footer")) request.setFooterText(metaNode.path("footer").asText());
            if (metaNode.has("cta_label")) request.setFlowCta(metaNode.path("cta_label").asText());
            if (metaNode.has("mode")) request.setMode(metaNode.path("mode").asText());

            if (metaNode.has("screen_id")) {
                flowActionPayload.setScreen(metaNode.path("screen_id").asText());
            }
            if (metaNode.has("data")) {
                Map<String, Object> dataMap = objectMapper.convertValue(metaNode.path("data"), new TypeReference<Map<String, Object>>() {});
                flowActionPayload.setData(dataMap);
            }
        }
        
        request.setFlowActionPayload(flowActionPayload);

        whatsAppService.sendInteractiveFlowMessage(request, systemUser).subscribe();
    }

    // --- CORREÇÃO 2: TEMPLATE STEP ---
    private void executeTemplateStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        
        String templateName = step.getContent();
        String metadataJson = step.getMetadata();

        SendTemplateMessageRequest request = new SendTemplateMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setTemplateName(templateName);
        request.setLanguageCode("pt_BR"); 

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            
            if (metaNode.has("language")) {
                request.setLanguageCode(metaNode.path("language").asText());
            }

            if (metaNode.has("components")) {
                // CORREÇÃO: Usar TypeReference para garantir a tipagem correta da Lista
                List<TemplateComponentRequest> components = objectMapper.convertValue(
                    metaNode.get("components"),
                    new TypeReference<List<TemplateComponentRequest>>() {}
                );
                // CORREÇÃO: Usar setResolvedComponents, pois são valores já definidos no JSON do bot, não mapeamentos dinâmicos
                request.setResolvedComponents(components);
            }
        }

        whatsAppService.sendTemplateMessage(request, systemUser, null).subscribe();
    }

    private void executeHandoffStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        String message = step.getContent();
        if (message == null || message.isBlank()) {
            message = "Aguarde um momento, estamos transferindo para um atendente humano.";
        }
        
        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message), systemUser).subscribe();
        
        sessionService.updateState(session, ConversationState.IN_SERVICE_HUMAN);
        session.setBotActive(false);
        session.setCurrentBotId(null);
        session.setCurrentStepId(null);
        sessionService.saveSession(session);
        log.info("Transbordo realizado para contato {}", contact.getPhoneNumber());
    }

    private void executeEndStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        String message = step.getContent();
        if (message != null && !message.isBlank()) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message), systemUser).subscribe();
        }
        sessionService.resetSession(session);
    }

    private void executeMediaStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        String mediaId = step.getContent();
        String type = "image";
        String caption = null;
        
        if (step.getMetadata() != null) {
            JsonNode node = objectMapper.readTree(step.getMetadata());
            if (node.has("type")) type = node.path("type").asText();
            if (node.has("caption")) caption = node.path("caption").asText();
        }
        
        SendMediaMessageRequest req = new SendMediaMessageRequest();
        req.setTo(contact.getPhoneNumber());
        req.setType(type);
        req.setMediaId(mediaId);
        req.setCaption(caption);
        
        whatsAppService.sendMediaMessage(req, systemUser).subscribe();
    }

    private boolean shouldTrigger(Bot bot) {
        if (bot.getTriggerType() == BotTriggerType.ALWAYS) return true;
        
        if (bot.getTriggerType() == BotTriggerType.OUT_OF_OFFICE_HOURS) {
            LocalTime now = LocalTime.now(); 
            return now.isBefore(bot.getStartTime()) || now.isAfter(bot.getEndTime());
        }
        return false;
    }

    private boolean checkMatch(String input, String keyword) {
        if (input == null || keyword == null) return false;
        return input.trim().equalsIgnoreCase(keyword.trim());
    }

    private SendTextMessageRequest createReq(String to, String body) {
        SendTextMessageRequest req = new SendTextMessageRequest();
        req.setTo(to);
        req.setMessage(body);
        return req;
    }
}
