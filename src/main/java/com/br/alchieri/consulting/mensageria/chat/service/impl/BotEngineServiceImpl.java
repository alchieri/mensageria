package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.br.alchieri.consulting.mensageria.chat.dto.request.SendFlowMessageRequest;
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
        // Busca bots ativos da empresa
        List<Bot> bots = botRepository.findByCompanyAndIsActiveTrue(company);

        for (Bot bot : bots) {
            if (shouldTrigger(bot)) {
                log.info("Iniciando Bot '{}' para {}", bot.getName(), contact.getPhoneNumber());
                
                // Inicia Sessão
                session.setBotActive(true);
                session.setCurrentBotId(bot.getId());
                
                // Executa o Passo Raiz
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
            sessionService.resetSession(session); // Estado inválido
            return;
        }

        // Procura nas opções qual bate com o input (match por keyword)
        Optional<BotOption> match = currentStep.getOptions().stream()
                .filter(opt -> checkMatch(input, opt.getKeyword()))
                .findFirst();

        if (match.isPresent()) {
            BotOption selectedOption = match.get();
            
            if (selectedOption.isHandoff()) {
                // Transbordo para Humano
                whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Transferindo para um atendente..."), systemUser).subscribe();
                sessionService.resetSession(session);
                // Aqui você setaria o status do chat para OPEN/PENDING no banco SQL
            } else if (selectedOption.getTargetStep() != null) {
                // Avança para o próximo passo
                executeStep(selectedOption.getTargetStep(), contact, session, systemUser);
            } else {
                // Opção fim de linha
                sessionService.resetSession(session);
            }
        } else {
            // Input inválido (não bateu com nenhuma opção)
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Opção inválida. Tente novamente."), systemUser).subscribe();
            // Reenvia o passo atual (opcional)
        }
    }

    // --- LÓGICA DE EXECUÇÃO DE PASSO ---

    private void executeStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        
        log.info("Executando passo bot: ID={}, Tipo={}, Contato={}", step.getId(), step.getStepType(), contact.getPhoneNumber());

        // 1. Atualiza Sessão (Persiste onde o usuário está)
        session.setCurrentStepId(step.getId());
        sessionService.saveSession(session);

        try {
            switch (step.getStepType()) {
                case TEXT:
                    executeTextStep(step, contact, systemUser);
                    break;

                case FLOW:
                    executeFlowStep(step, contact, systemUser);
                    break;

                case TEMPLATE:
                    executeTemplateStep(step, contact, systemUser);
                    break;
                
                case MEDIA: 
                   executeMediaStep(step, contact, systemUser);
                   break;

                case HANDOFF:
                    executeHandoffStep(step, contact, session, systemUser);
                    break;

                case END:
                    executeEndStep(step, contact, session, systemUser);
                    break;

                default:
                    log.warn("Tipo de passo desconhecido: {}", step.getStepType());
                    whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Erro: Tipo de passo não suportado."), systemUser).subscribe();
                    break;
            }
        } catch (Exception e) {
            log.error("Erro crítico ao executar passo do bot ID {}: {}", step.getId(), e.getMessage(), e);
            // Fallback de erro
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), "Desculpe, ocorreu um erro técnico no bot."), systemUser).subscribe();
        }
    }

    // --- IMPLEMENTAÇÃO DOS TIPOS ESPECÍFICOS ---

    /**
     * TEXTO + MENU:
     * Envia o texto. Se houver opções, formata como lista numerada.
     * (Futuramente pode evoluir para Interactive Buttons se options.size() <= 3)
     */
    private void executeTextStep(BotStep step, Contact contact, User systemUser) {
        StringBuilder body = new StringBuilder(step.getContent());

        // Se houver opções, montamos um menu textual
        if (step.getOptions() != null && !step.getOptions().isEmpty()) {
            body.append("\n\n");
            // Ordena pela sequência
            step.getOptions().sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

            for (BotOption opt : step.getOptions()) {
                // Ex: "1 - Financeiro"
                // Se a keyword for igual ao label ou vazia, usa sequência.
                String displayKey = opt.getKeyword();
                body.append("👉 *").append(displayKey).append("* - ").append(opt.getLabel()).append("\n");
            }
        }

        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), body.toString()), systemUser).subscribe();
    }

    /**
     * FLOW:
     * Envia uma mensagem interativa com botão para abrir o Flow.
     * Metadata esperado: { "flow_token": "...", "cta_label": "Abrir", "screen_id": "screen_01", "data": {...} }
     * Content esperado: Flow ID (da Meta)
     */
    private void executeFlowStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        
        String flowIdentifier = step.getContent(); // ID ou Nome do Flow
        String metadataJson = step.getMetadata();
        
        SendInteractiveFlowMessageRequest request = new SendInteractiveFlowMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setFlowName(flowIdentifier);
        
        // Define um token de rastreamento único para este passo do bot
        // Isso ajuda no webhook a saber que a resposta veio deste passo específico
        request.setFlowToken("BOT_STEP_" + step.getId());
        
        // Configuração padrão
        request.setFlowAction("navigate");
        request.setMode("published");
        request.setBodyText("Por favor, continue o atendimento no formulário abaixo.");
        request.setFlowCta("Abrir");

        // Sobrescreve com Metadata se existir
        Map<String, Object> flowActionPayload = new HashMap<>();
        flowActionPayload.put("screen", "SUCCESS"); // Tela padrão fallback

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            
            if (metaNode.has("header")) request.setHeaderText(metaNode.path("header").asText());
            if (metaNode.has("body")) request.setBodyText(metaNode.path("body").asText());
            if (metaNode.has("footer")) request.setFooterText(metaNode.path("footer").asText());
            if (metaNode.has("cta_label")) request.setFlowCta(metaNode.path("cta_label").asText());
            if (metaNode.has("mode")) request.setMode(metaNode.path("mode").asText());

            // Payload da Ação (Screen + Data)
            if (metaNode.has("screen_id")) {
                flowActionPayload.put("screen", metaNode.path("screen_id").asText());
            }
            if (metaNode.has("data")) {
                // Converte o nó "data" em Map<String, Object>
                Map<String, Object> dataMap = objectMapper.convertValue(metaNode.path("data"), Map.class);
                flowActionPayload.put("data", dataMap);
            }
        }
        request.setFlowActionPayload(flowActionPayload);

        // Chama o serviço
        whatsAppService.sendInteractiveFlowMessage(request, systemUser).subscribe();
    }

    /**
     * TEMPLATE:
     * Envia um Template HSM (aprovado pela Meta).
     * Content esperado: Nome do Template
     * Metadata esperado: { "language": "pt_BR", "components": [ ... ] }
     */
    private void executeTemplateStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        
        String templateName = step.getContent();
        String metadataJson = step.getMetadata();

        SendTemplateMessageRequest request = new SendTemplateMessageRequest();
        request.setTo(contact.getPhoneNumber());
        request.setTemplateName(templateName);
        request.setLanguageCode("pt_BR"); // Default

        if (metadataJson != null && !metadataJson.isBlank()) {
            JsonNode metaNode = objectMapper.readTree(metadataJson);
            
            if (metaNode.has("language")) {
                request.setLanguageCode(metaNode.path("language").asText());
            }

            if (metaNode.has("components")) {
                // Mágica do Jackson: Converte o array JSON "components" diretamente 
                // para a Lista de TemplateComponentRequest
                List<TemplateComponentRequest> components = objectMapper.convertValue(
                    metaNode.get("components"),
                    new TypeReference<List<TemplateComponentRequest>>() {}
                );
                request.setComponents(components);
            }
        }

        // Chama o serviço
        whatsAppService.sendTemplateMessage(request, systemUser, null).subscribe();
    }

    /**
     * HANDOFF (Transbordo):
     * Envia mensagem de transferência e muda o estado da sessão.
     */
    private void executeHandoffStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        String message = step.getContent();
        if (message == null || message.isBlank()) {
            message = "Aguarde um momento, estamos transferindo para um atendente humano.";
        }
        
        whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message), systemUser).subscribe();
        
        // LÓGICA CRUCIAL:
        // 1. Muda estado da sessão para IN_SERVICE_HUMAN
        sessionService.updateState(session, ConversationState.IN_SERVICE_HUMAN);
        
        // 2. Desativa flag de bot para parar o motor
        session.setBotActive(false);
        session.setCurrentBotId(null);
        session.setCurrentStepId(null);
        sessionService.saveSession(session);
        
        // 3. (Opcional) Dispara notificação/WebSocket para painel de atendimento aqui
        log.info("Transbordo realizado para contato {}", contact.getPhoneNumber());
    }

    /**
     * END (Fim):
     * Envia tchau e reseta a sessão para IDLE.
     */
    private void executeEndStep(BotStep step, Contact contact, UserSession session, User systemUser) {
        String message = step.getContent();
        if (message != null && !message.isBlank()) {
            whatsAppService.sendTextMessage(createReq(contact.getPhoneNumber(), message), systemUser).subscribe();
        }
        sessionService.resetSession(session);
    }

    // --- SE TIVER MÍDIA (Imagem/PDF) ---
    private void executeMediaStep(BotStep step, Contact contact, User systemUser) throws JsonProcessingException {
        String mediaId = step.getContent(); // ID da mídia no WhatsApp
        String type = "image"; // Default
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

    // --- AUXILIARES ---

    private boolean shouldTrigger(Bot bot) {
        if (bot.getTriggerType() == BotTriggerType.ALWAYS) return true;
        
        if (bot.getTriggerType() == BotTriggerType.OUT_OF_OFFICE_HOURS) {
            LocalTime now = LocalTime.now(); // Cuidar com Timezone da empresa!
            return now.isBefore(bot.getStartTime()) || now.isAfter(bot.getEndTime());
        }
        return false;
    }

    private boolean checkMatch(String input, String keyword) {
        if (input == null || keyword == null) return false;
        // Comparação simples (pode evoluir para Regex ou Contains)
        return input.trim().equalsIgnoreCase(keyword.trim());
    }

    private SendTextMessageRequest createReq(String to, String body) {
        SendTextMessageRequest req = new SendTextMessageRequest();
        req.setTo(to);
        req.setMessage(body);
        return req;
    }
}
