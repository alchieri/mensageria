package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.dto.webhook.WebhookEventPayload;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.chat.model.FlowHealthAlert;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledMessage;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowDataRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowHealthAlertRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledMessageRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.WhatsAppMessageLogRepository;
import com.br.alchieri.consulting.mensageria.chat.service.BotEngineService;
import com.br.alchieri.consulting.mensageria.chat.service.CallbackService;
import com.br.alchieri.consulting.mensageria.chat.service.FlowService;
import com.br.alchieri.consulting.mensageria.chat.service.WebhookService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.CompanyTierStatus;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.model.redis.UserSession;
import com.br.alchieri.consulting.mensageria.repository.CompanyTierStatusRepository;
import com.br.alchieri.consulting.mensageria.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.repository.WhatsAppPhoneNumberRepository;
import com.br.alchieri.consulting.mensageria.service.AdminNotificationService;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.br.alchieri.consulting.mensageria.service.impl.SessionService;
import com.br.alchieri.consulting.mensageria.util.SignatureUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final ObjectMapper objectMapper;
    private final SignatureUtil signatureUtil;
    private final SqsTemplate sqsTemplate;
    
    private final WhatsAppMessageLogRepository messageLogRepository;
    // private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final ScheduledMessageRepository scheduledMessageRepository;
    private final FlowHealthAlertRepository flowHealthAlertRepository;
    private final FlowRepository flowRepository;
    private final CompanyTierStatusRepository companyTierStatusRepository;
    private final FlowDataRepository flowDataRepository;
    private final UserRepository userRepository;
    private final WhatsAppPhoneNumberRepository phoneNumberRepository;
    
    private final CallbackService callbackService;
    private final AdminNotificationService adminNotificationService;
    private final FlowService flowService;
    private final BillingService billingService;
    private final SessionService sessionService;
    // private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final BotEngineService botEngineService;

    @Value("${webhook-queue.name}")
    private String webhookQueueName;

    @Override
    public boolean verifySignature(String payload, String signatureHeader) {
        return signatureUtil.verifySignature(payload, signatureHeader);
    }

    @Override
    public void queueWebhookEvent(String payload) {
        log.info("Queueing webhook event payload.");
        try {
            // Valida se o payload é um JSON válido antes de enfileirar
            objectMapper.readTree(payload);
            sqsTemplate.send(webhookQueueName, payload);
            log.info("Webhook event successfully queued.");
        } catch (JsonProcessingException e) {
            log.error("Invalid JSON payload received, not queueing: {}", payload, e);
        } catch (Exception e) {
            log.error("Error queueing webhook payload", e);
            throw new RuntimeException("Failed to queue webhook event", e);
        }
    }

    @Override
    public void processWebhookPayload(String payload) {
        log.debug("Processando payload do webhook...");
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            log.trace("Payload JSON parseado: {}", rootNode);

            if (rootNode.has("object") && "whatsapp_business_account".equals(rootNode.path("object").asText())) {
                if (rootNode.has("entry")) {
                    for (JsonNode entry : rootNode.get("entry")) {
                        // Processa cada 'change' dentro de uma transação separada (REQUIRES_NEW)
                        // para isolar falhas em um change das outras.
                        processEntryChanges(entry);
                    }
                } else {
                    log.warn("Webhook payload sem nó 'entry': {}", rootNode);
                }
            } else {
                log.warn("Payload do webhook não corresponde à estrutura esperada (object=whatsapp_business_account). Payload: {}", payload);
            }
        } catch (JsonProcessingException e) {
            log.error("Erro Crítico: Falha ao fazer parse do JSON do payload do webhook: {}", payload, e);
        } catch (Exception e) {
            log.error("Erro inesperado ao processar payload do webhook: {}", payload, e);
        }
    }

    @Override
    public void queueWebhookEvent(String payload, String signature) {
        log.info("Queueing webhook event payload.");
        try {
            // 1. Criar um objeto wrapper para a fila que contém TUDO que o consumidor precisa
            WebhookEventPayload queuePayload = WebhookEventPayload.builder()
                    .rawPayload(payload) // <<< O JSON BRUTO da Meta
                    .signature(signature) // A assinatura para verificação posterior
                    .receivedTimestamp(LocalDateTime.now())
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(queuePayload);
            
            // 2. Enfileirar o objeto wrapper
            sqsTemplate.send(to -> to.queue(webhookQueueName)
                                      .payload(jsonPayload)
                                      .header("message-group-id", "webhook-events")); // Message group para FIFO
            
            log.info("Webhook event successfully queued.");

        } catch (Exception e) {
            log.error("Falha ao enfileirar evento de webhook. O evento será perdido. Erro: {}", e.getMessage(), e);
            // Lançar exceção aqui faria o WebhookController retornar 500, o que faria a Meta tentar novamente.
            // Isso pode ser desejável.
            throw new RuntimeException("Falha ao enfileirar evento de webhook.", e);
        }
    }

    @Override
    public void processWebhookPayload(String payload, String signature) {
        // 1. A primeira coisa a fazer é verificar a assinatura
        if (!signatureUtil.verifySignature(payload, signature)) {
            log.warn("Assinatura do webhook inválida no consumidor SQS. Descartando mensagem.");
            // Não lançar exceção para que a mensagem seja removida da fila e não vá para a DLQ
            return;
        }
        log.info("Assinatura do webhook verificada com sucesso no consumidor.");

        // 2. Agora, o resto da lógica pode prosseguir como antes
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            
            if (rootNode.has("object") && "whatsapp_business_account".equals(rootNode.path("object").asText())) {
                if (rootNode.has("entry")) {
                    for (JsonNode entry : rootNode.get("entry")) {
                        // O método processEntryChanges já é @Transactional
                        processEntryChanges(entry);
                    }
                } else { /* log warn */ }
            } else {
                log.warn("Payload do webhook (da fila) não corresponde à estrutura esperada (object=whatsapp_business_account). Payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("Erro ao processar payload de webhook da fila: {}", e.getMessage(), e);
            // Lançar exceção para que a mensagem seja reenviada ou vá para a DLQ
            throw new RuntimeException("Erro de processamento de webhook.", e);
        }
    }

    // Processa changes de uma entry com transação própria
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEntryChanges(JsonNode entry) {
        if (entry.has("changes")) {
            for (JsonNode change : entry.get("changes")) {
                try {
                    String field = change.path("field").asText();
                    String wabaId = entry.path("id").asText(null);
                    JsonNode value = change.path("value");

                    if (!value.isMissingNode()) {
                        switch (field) {
                            case "messages":
                                String webhookPhoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                                
                                WhatsAppPhoneNumber channel = findChannelByMetaId(webhookPhoneNumberId);
                                
                                if (channel != null) {
                                    processMessagesFieldValue(value, channel);
                                } else {
                                    log.warn("Ignorando mensagens para ID desconhecido: {}", webhookPhoneNumberId);
                                }
                                break;
                            case "flows":
                                handleFlowsField(value, wabaId);
                                break;
                            case "account_update":
                                handleAccountUpdateField(value, wabaId);
                                break;
                            default:
                                log.warn("Webhook field unknown: {}", field);
                                break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing entry change", e);
                }
            }
        } else {
            log.warn("Entry do webhook sem nó 'changes': {}", entry);
        }
    }

    private void handleFlowsField(JsonNode valueNode, String wabaId) {

        String eventType = valueNode.path("event").asText(null);
        String metaFlowId = valueNode.path("flow_id").asText(null);

        if (eventType == null || metaFlowId == null) {
            log.warn("Webhook de 'flows' recebido com dados incompletos (event ou flow_id ausente): {}", valueNode);
            return;
        }

        log.info("Webhook de Flow recebido: Evento='{}', FlowID='{}', WABA_ID='{}'", eventType, metaFlowId, wabaId);

        // Encontra o Flow no nosso banco de dados
        Optional<Flow> optFlow = flowRepository.findByMetaFlowId(metaFlowId);
        if (optFlow.isEmpty()) {
            log.warn("Recebido evento de Flow para o Meta Flow ID '{}', mas este Flow não está registrado em nosso sistema.", metaFlowId);
            // Poderíamos criar um alerta "órfão" aqui se quiséssemos
            return;
        }
        Flow flow = optFlow.get();
        Company company = flow.getCompany();

         if (company == null) {
            log.warn("Flow ID {} encontrado, mas não está associado a nenhuma empresa. Alerta não pode ser processado completamente.", flow.getMetaFlowId());
            return;
        }

        // 1. Salva o alerta de saúde no banco para auditoria
        FlowHealthAlert alert = new FlowHealthAlert();
        alert.setFlow(flow);
        alert.setMetaFlowId(metaFlowId);
        alert.setEventType(eventType);
        alert.setMessage(valueNode.path("message").asText(null));
        alert.setAlertState(valueNode.path("alert_state").asText(null));
        try {
            alert.setEventDataJson(objectMapper.writeValueAsString(valueNode));
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar dados do alerta de Flow para o banco.", e);
        }
        flowHealthAlertRepository.save(alert);
        log.info("Alerta de saúde para o Flow ID {} salvo no banco.", flow.getId());

        String alertState = null;
        // 2. Lógica de Negócio Específica por Evento
        switch (eventType) {
            case "FLOW_STATUS_CHANGE":
                String newStatusStr = valueNode.path("new_status").asText();
                log.info("Status do Flow '{}' (ID {}) alterado para {}.", flow.getName(), flow.getId(), newStatusStr);
                try {
                    // Mapeia o status da Meta para o nosso enum
                    // Ex: "PUBLISHED" -> FlowStatus.PUBLISHED
                    FlowStatus newStatus = FlowStatus.valueOf(newStatusStr.toUpperCase());
                    
                    // Apenas o webhook deve setar os status de saúde do sistema
                    if (List.of(FlowStatus.THROTTLED, FlowStatus.BLOCKED).contains(newStatus) || 
                        // Também trata a recuperação (Unthrottle/Unblock)
                        (flow.getStatus() == FlowStatus.THROTTLED && newStatus == FlowStatus.PUBLISHED) ||
                        (flow.getStatus() == FlowStatus.BLOCKED && newStatus == FlowStatus.THROTTLED))
                    {
                        flow.setStatus(newStatus);
                        flowRepository.save(flow);
                        
                        // Dispara callback para o cliente sobre a mudança de status
                        callbackService.sendFlowStatusCallback(flow.getCompany().getId(), flow.getId());
                    } else {
                        log.info("Mudança de status '{}' para '{}' é gerenciada pelo usuário via API, ignorando webhook para este campo para evitar conflitos.",
                                valueNode.path("old_status").asText(), newStatusStr);
                        // Apenas logamos, mas não alteramos o status se for uma mudança que o usuário fez (ex: DRAFT -> PUBLISHED),
                        // pois nosso serviço já deve ter atualizado o status. Isso evita corridas de condição.
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Status de Flow '{}' recebido da Meta não é reconhecido pelo nosso sistema.", newStatusStr);
                }
                break;

            case "ENDPOINT_ERROR_RATE":
            case "ENDPOINT_AVAILABILITY":
                alertState = valueNode.path("alert_state").asText();
                if ("ACTIVATED".equalsIgnoreCase(alertState)) {
                    // Agora podemos acessar company.getName() sem erro
                    adminNotificationService.notifyCallbackFailure(
                        "Alerta de Saúde do WhatsApp Flow Ativado",
                        String.format("Flow: %s (ID: %d)\nEmpresa: %s (ID: %d)\nEvento: %s\nMensagem: %s",
                                      flow.getName(), flow.getId(),
                                      flow.getCompany().getName(), flow.getCompany().getId(),
                                      eventType, alert.getMessage())
                    );
                }
                break;
            case "ENDPOINT_LATENCY":
            case "CLIENT_ERROR_RATE":
                alertState = valueNode.path("alert_state").asText();
                if ("ACTIVATED".equalsIgnoreCase(alertState)) {
                    
                    // Notifica o admin do BSP
                    adminNotificationService.notifyCallbackFailure(
                        "Alerta de Saúde do WhatsApp Flow Ativado",
                        String.format("Flow: %s (ID: %d)\nEmpresa: %s (ID: %d)\nEvento: %s\nMensagem: %s",
                                      flow.getName(), flow.getId(),
                                      flow.getCompany().getName(), flow.getCompany().getId(),
                                      eventType, alert.getMessage())
                    );

                    log.info("Alerta de saúde ATIVADO para Flow ID {}. Disparando sincronização de status com a Meta.", flow.getId());
                    flowService.fetchAndSyncFlowStatus(flow.getId(), flow.getCompany())
                        .doOnError(e -> log.error("Falha ao tentar sincronizar status do Flow ID {} após alerta: {}", flow.getId(), e.getMessage()))
                        .subscribe(); // .subscribe() para acionar o Mono, já que estamos em um método void

                } else if ("DEACTIVATED".equalsIgnoreCase(alertState)) {
                    log.info("Alerta de saúde DESATIVADO para Flow ID {}: Evento='{}'. Disparando sincronização de status.", flow.getId(), eventType);
                    // Também é uma boa ideia sincronizar quando o alerta é desativado,
                    // pois o status pode ter voltado de BLOCKED para PUBLISHED.
                    flowService.fetchAndSyncFlowStatus(flow.getId(), flow.getCompany())
                        .doOnError(e -> log.error("Falha ao tentar sincronizar status do Flow ID {} após desativação de alerta: {}", flow.getId(), e.getMessage()))
                        .subscribe();
                }
                break;
            case "FLOW_VERSION_EXPIRY_WARNING":
                log.warn("AVISO DE EXPIRAÇÃO DE VERSÃO para Flow ID {}: {}", flow.getId(), valueNode.path("warning").asText());
                // TODO: Notificar admin do BSP e/ou cliente sobre a necessidade de atualizar o Flow.
                break;
        }
    }

    private void processMessagesFieldValue(JsonNode valueNode, WhatsAppPhoneNumber channel) {

        Company company = channel.getCompany();
        if (valueNode.has("statuses")) {
            for (JsonNode statusNode : valueNode.get("statuses")) {
                try { 
                    handleMessageStatusUpdate(statusNode, company);
                } catch (Exception e) { 
                    log.error("Erro ao processar status individual: {}", statusNode, e);
                }
            }
        } else if (valueNode.has("messages")) {
            JsonNode contactsNode = valueNode.get("contacts");
            String ourPhoneNumber = valueNode.path("metadata").path("display_phone_number").asText(null);
            for (JsonNode messageNode : valueNode.get("messages")) {
                 try {
                    handleIncomingMessage(messageNode, contactsNode, ourPhoneNumber, company, channel);
                } catch (Exception e) {
                    log.error("Erro ao processar mensagem recebida individual: {}", messageNode, e);
                }
            }
        } else { 
            log.warn("Nó 'value' do webhook de mensagem não continha 'statuses' nem 'messages'. Value: {}", valueNode.toString());
        }
    }

    @Transactional
    private void handleMessageStatusUpdate(JsonNode statusNode, Company companyFromWebhookHint) { // Removido companyFromWebhook, pegaremos do log
        
        String wamid = statusNode.path("id").asText(null);
        if (wamid == null || wamid.isBlank()) {
            log.warn("WAMID ausente na atualização de status: {}", statusNode);
            return;
        }

        String status = statusNode.path("status").asText();
        if (status == null || status.isBlank()) {
            log.warn("Status ausente na atualização de status para WAMID {}: {}", wamid, statusNode);
            return;
        }
        String statusUpper = status.toUpperCase();
        long timestampEpochSeconds = statusNode.path("timestamp").asLong();
        LocalDateTime statusTimestamp = Instant.ofEpochSecond(timestampEpochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();

        log.info("Processando atualização de status: WAMID={}, NovoStatus={}", wamid, statusUpper);

        // 1. Encontra e atualiza o log principal (WhatsAppMessageLog)
        Optional<WhatsAppMessageLog> optLog = messageLogRepository.findByWamid(wamid);

        if (optLog.isEmpty()) {
            log.warn("Log de mensagem OUTGOING não encontrado para atualização de status com WAMID: {}. Não é possível atualizar status.", wamid);
            // Não podemos prosseguir se não acharmos o log original
            return;
        }

        WhatsAppMessageLog msgLog = optLog.get();
        msgLog.setStatus(statusUpper);
        msgLog.setUpdatedAt(statusTimestamp);

        // 2. Atualiza o metadata com o erro, se houver
        if ("FAILED".equals(statusUpper) && statusNode.has("errors")) {
            try {
                msgLog.setMetadata(objectMapper.writeValueAsString(statusNode.get("errors")));
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar erros de status para WAMID {}: {}", wamid, e.getMessage());
                msgLog.setMetadata("Erro ao serializar detalhes da falha.");
            }
        } else {
            // Limpa o metadata se o status não for 'failed' (opcional, pode ser útil manter o último erro)
            // msgLog.setMetadata(null);
        }
        JsonNode pricingNode = statusNode.path("pricing");
        if (!pricingNode.isMissingNode()) {
            boolean billable = pricingNode.path("billable").asBoolean(false);
            String pricingCategory = pricingNode.path("category").asText(null);
            
            if (pricingCategory != null) msgLog.setPricingCategory(pricingCategory);

            if (billable) {
                BigDecimal metaCost = billingService.calculateMetaCostForMessage(msgLog);
                BigDecimal platformFee = billingService.calculatePlatformFee(msgLog); // Pode precisar do metaCost como parâmetro
                
                msgLog.setMetaCost(metaCost);
                msgLog.setPlatformFee(platformFee);
                msgLog.setFinalPrice(metaCost.add(platformFee));
                
                // Atualizar o contador de custos mensais no BillingPlan
                billingService.recordCosts(msgLog.getCompany(), metaCost, platformFee);
            } else {
                msgLog.setMetaCost(BigDecimal.ZERO);
                msgLog.setPlatformFee(BigDecimal.ZERO); // Ou sua taxa mínima
                msgLog.setFinalPrice(BigDecimal.ZERO);
            }
        }
        messageLogRepository.save(msgLog);
        log.debug("WhatsAppMessageLog ID {} (WAMID {}) atualizado para status {}", msgLog.getId(), wamid, statusUpper);


        // 3. Se o log estiver vinculado a uma mensagem agendada, atualiza-a também
        if (msgLog.getScheduledMessageId() != null) {
            scheduledMessageRepository.findById(msgLog.getScheduledMessageId()).ifPresent(scheduledMsg -> {
                try {
                    // Mapeia o status da Meta para o status da mensagem agendada
                    ScheduledMessage.MessageStatus scheduledStatus = ScheduledMessage.MessageStatus.valueOf(statusUpper);
                    scheduledMsg.setStatus(scheduledStatus);
                    if ("FAILED".equals(statusUpper)) {
                        scheduledMsg.setFailureReason(msgLog.getMetadata()); // Copia o motivo da falha
                    }
                    scheduledMessageRepository.save(scheduledMsg);
                    log.info("Status da ScheduledMessage ID {} atualizado para {} (via WAMID {}).",
                             scheduledMsg.getId(), statusUpper, wamid);
                } catch (IllegalArgumentException e) {
                    log.warn("Não foi possível mapear o status '{}' da Meta para um status de ScheduledMessage. O status da mensagem agendada não foi alterado.", statusUpper);
                }
            });
        }

        // 4. Determina a empresa para o callback e dispara a notificação
        Company companyForCallback = msgLog.getCompany() != null ? msgLog.getCompany() : companyFromWebhookHint;
        if (companyForCallback != null) {
            callbackService.sendStatusCallback(companyForCallback.getId(), msgLog.getId());
        } else {
            log.warn("Callback de status para WAMID {} não enviado: Nenhuma empresa associada encontrada.", wamid);
        }
    }

    @Transactional
    private void handleIncomingMessage(JsonNode messageNode, JsonNode contactsNode, String ourPhoneNumber, Company companyAssociatedWithWebhook, WhatsAppPhoneNumber channel) {
        
        String wamid = messageNode.path("id").asText();
        if (wamid == null || wamid.isBlank()) { 
            log.warn("WAMID ausente na mensagem recebida: {}", messageNode);
            return;
        }
        // Evita salvar duplicatas
        if (messageLogRepository.findByWamid(wamid).isPresent()) {
             log.warn("Mensagem recebida com WAMID {} já existe no log. Ignorando webhook duplicado.", wamid);
             return;
        }

        String from = messageNode.path("from").asText();
        String type = messageNode.path("type").asText();
        long ts = messageNode.path("timestamp").asLong();
        LocalDateTime messageTimestamp = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDateTime();

        log.info("Processando mensagem recebida: WAMID={}, From={}, Type={}, Empresa={}", 
                wamid, from, type, companyAssociatedWithWebhook != null ? companyAssociatedWithWebhook.getId() : "N/A");

        // ---------------------------------------------------------
        // 1. Persistência do Log (Mantendo sua lógica original robusta)
        // ---------------------------------------------------------
        WhatsAppMessageLog newLog = new WhatsAppMessageLog();
        newLog.setWamid(wamid);
        newLog.setCompany(companyAssociatedWithWebhook);
        newLog.setDirection(MessageDirection.INCOMING);
        newLog.setChannelId(channel.getPhoneNumberId());
        newLog.setSenderPhoneNumber(from);
        newLog.setRecipient(ourPhoneNumber);
        newLog.setMessageType(type.toUpperCase());
        newLog.setStatus("RECEIVED");
        newLog.setCreatedAt(messageTimestamp);
        newLog.setUpdatedAt(messageTimestamp);

        try { 
            extractMessageContentAndMetadata(messageNode, newLog); 
        } catch (Exception e) {
            log.error("Erro ao extrair conteúdo/metadata da mensagem recebida {}: {}", wamid, e.getMessage());
            newLog.setContent("Erro ao processar conteúdo.");
            newLog.setMetadata("{\"error\":\"Falha ao processar payload da mensagem.\"}");
        }

        WhatsAppMessageLog savedLog = messageLogRepository.save(newLog);
        log.debug("Log salvo com ID {}", savedLog.getId());

        // ---------------------------------------------------------
        // 2. Resolução de Contato (Mantendo sua lógica original 9º Dígito)
        // ---------------------------------------------------------
        String profileName = "Contato " + from; 
        if (contactsNode != null && contactsNode.isArray() && !contactsNode.isEmpty()) {
            profileName = contactsNode.get(0).path("profile").path("name").asText(profileName);
        }
        Contact contact = findOrSaveContact(companyAssociatedWithWebhook, from, profileName);
        
        contact.setUnreadMessagesCount(contact.getUnreadMessagesCount() + 1);
        contact.setUpdatedAt(LocalDateTime.now());
        contact.setLastActiveAt(LocalDateTime.now());
        contactRepository.save(contact);

        // ---------------------------------------------------------
        // 3. Processamento Específico de Flows (Mantendo original)
        // ---------------------------------------------------------
        if ("interactive".equals(type) && messageNode.has("interactive")) {
            JsonNode interactiveNode = messageNode.path("interactive");
            if ("nfm_reply".equals(interactiveNode.path("type").asText())) {
                try {
                    processAndSaveFlowData(interactiveNode, companyAssociatedWithWebhook, contact, from);
                } catch (Exception e) {
                    log.error("ERRO AO SALVAR FLOW DATA para WAMID {}: {}", wamid, e.getMessage(), e);
                }
            }
        }

        // ---------------------------------------------------------
        // 4. Integração do BOT (ESTADO E SESSÃO) - NOVO
        // ---------------------------------------------------------
        if (companyAssociatedWithWebhook != null) {
            try {
                
                UserSession session = sessionService.getSession(companyAssociatedWithWebhook, contact.getPhoneNumber());
                User systemUser = getSystemUserForCompany(companyAssociatedWithWebhook);
                
                String botInput = extractContentForBot(messageNode, type);

                // CENÁRIO 1: Usuário já está preso num bot ativo?
                if (session.isBotActive()) {
                    botEngineService.processInput(botInput, contact, session, systemUser);
                } 
                // CENÁRIO 2: Não está em bot. Devemos iniciar um?
                else {
                    boolean botStarted = botEngineService.tryTriggerBot(companyAssociatedWithWebhook, contact, session, systemUser);
                    
                    if (!botStarted) {
                        // Se nenhum bot assumiu, cai no fallback (atendimento humano ou auto-resposta padrão simples)
                        log.info("Nenhum bot ativo para o cenário atual. Mensagem entregue para caixa de entrada.");
                    }
                }
            } catch (Exception e) {
                log.error("Erro no motor de bot: {}", e.getMessage(), e);
            }
        }

        // ---------------------------------------------------------
        // 5. Callbacks (Mantendo original)
        // ---------------------------------------------------------
        if (companyAssociatedWithWebhook != null && companyAssociatedWithWebhook.getGeneralCallbackUrl() != null && !companyAssociatedWithWebhook.getGeneralCallbackUrl().isBlank()) {
            callbackService.sendIncomingMessageCallback(companyAssociatedWithWebhook.getId(), savedLog.getId());
        } else {
            log.debug("Callback não enviado: URL ausente ou empresa nula.");
        }
    }

    // private void processBotFlow(ConversationState state, String input, String type, Contact contact, UserSession session, Company company) {
    //     User systemUser = getSystemUserForCompany(company); 

    //     switch (state) {
    //         case IDLE:
    //             if (isGreeting(input)) {
    //                 sendMainMenu(contact, systemUser);
    //                 sessionService.updateState(session, ConversationState.WAITING_MENU_OPTION);
    //             }
    //             break;

    //         case WAITING_MENU_OPTION:
    //             handleMenuOption(input, contact, session, systemUser);
    //             break;

    //         case WAITING_DOCUMENT:
    //             // Exemplo simples
    //             if (input != null && input.replaceAll("\\D", "").length() == 11) {
    //                 session.addContextData("cpf", input);
    //                 whatsAppCloudApiService.sendTextMessage(
    //                     createRequest(contact.getPhoneNumber(), "CPF recebido! Simulando consulta..."), 
    //                     systemUser
    //                 ).subscribe();
    //                 sessionService.resetSession(session);
    //             } else {
    //                  whatsAppCloudApiService.sendTextMessage(
    //                     createRequest(contact.getPhoneNumber(), "CPF inválido. Tente novamente."), 
    //                     systemUser
    //                 ).subscribe();
    //             }
    //             break;
                
    //         default:
    //             // Se estado desconhecido, reseta (fail-safe)
    //             sessionService.resetSession(session);
    //             break;
    //     }
    // }

    // private void handleMenuOption(String input, Contact contact, UserSession session, User systemUser) {
        
    //     String option = input.trim().toLowerCase();

    //     if (option.equals("1") || option.contains("financeiro")) {
    //         whatsAppCloudApiService.sendTextMessage(
    //             createRequest(contact.getPhoneNumber(), "Opção Financeiro selecionada. Digite seu CPF:"), 
    //             systemUser
    //         ).subscribe();
    //         sessionService.updateState(session, ConversationState.WAITING_DOCUMENT);

    //     } else if (option.equals("2") || option.contains("suporte")) {
    //         whatsAppCloudApiService.sendTextMessage(
    //             createRequest(contact.getPhoneNumber(), "Transferindo para humano..."), 
    //             systemUser
    //         ).subscribe();
    //         sessionService.updateState(session, ConversationState.IN_SERVICE_HUMAN);

    //     } else {
    //         whatsAppCloudApiService.sendTextMessage(
    //             createRequest(contact.getPhoneNumber(), "Opção inválida. Digite 1 (Financeiro) ou 2 (Suporte)."), 
    //             systemUser
    //         ).subscribe();
    //     }
    // }

    // private void sendMainMenu(Contact contact, User systemUser) {
    //     String menu = "Olá " + contact.getName() + "! Escolha uma opção:\n1️⃣ Financeiro\n2️⃣ Suporte";
    //     whatsAppCloudApiService.sendTextMessage(createRequest(contact.getPhoneNumber(), menu), systemUser).subscribe();
    // }

    private String extractContentForBot(JsonNode messageNode, String type) {
        if ("text".equals(type)) {
            return messageNode.path("text").path("body").asText();
        } else if ("interactive".equals(type)) {
            JsonNode interactive = messageNode.path("interactive");
            String subType = interactive.path("type").asText();
            if ("button_reply".equals(subType)) return interactive.path("button_reply").path("id").asText();
            if ("list_reply".equals(subType)) return interactive.path("list_reply").path("id").asText();
        }
        return "";
    }

    // private boolean isGreeting(String text) {
    //     if (text == null) return false;
    //     String t = text.toLowerCase();
    //     return t.contains("oi") || t.contains("olá") || t.contains("ola") || t.contains("bom dia") || t.contains("boa tarde") || t.equals("menu");
    // }
    
    // private SendTextMessageRequest createRequest(String to, String body) {
    //     SendTextMessageRequest req = new SendTextMessageRequest();
    //     req.setTo(to);
    //     req.setMessage(body);
    //     return req;
    // }
    
    private User getSystemUserForCompany(Company company) {
        
        return userRepository.findFirstByCompanyAndRolesContaining(company, Role.ROLE_COMPANY_ADMIN)
                // Se não achar, tenta buscar um Admin do BSP vinculado à empresa (fallback)
                .or(() -> userRepository.findFirstByCompanyAndRolesContaining(company, Role.ROLE_BSP_ADMIN))
                .orElseThrow(() -> new BusinessException("Empresa " + company.getId() + " não possui usuários administradores válidos para envio de bot."));
    }

    /**
     * Método centralizado para lidar com a inconsistência do nono dígito no Brasil.
     * Tenta achar o contato de todas as formas possíveis antes de criar um novo.
     */
    private Contact findOrSaveContact(Company company, String incomingNumber, String profileName) {
        // 1. Tentativa Direta (Exatamente como veio)
        Optional<Contact> optContact = contactRepository.findByCompanyAndPhoneNumber(company, incomingNumber);

        // 2. Lógica Brasil (DDI 55)
        if (optContact.isEmpty() && incomingNumber.startsWith("55")) {
            // Caso A: Veio com 12 dígitos (55 + DDD + 8 números). Ex: 554588887777
            // O sistema tenta achar a versão COM 9 (55 + DDD + 9 + 8 números).
            if (incomingNumber.length() == 12) {
                String numberWithNine = incomingNumber.substring(0, 4) + "9" + incomingNumber.substring(4);
                optContact = contactRepository.findByCompanyAndPhoneNumber(company, numberWithNine);
                
                if (optContact.isPresent()) {
                    log.debug("Contato encontrado adicionando 9º dígito: {} -> {}", incomingNumber, numberWithNine);
                }
            }
            // Caso B: Veio com 13 dígitos (55 + DDD + 9 + 8 números). Ex: 5545988887777
            // O sistema tenta achar a versão SEM 9 (para casos legados ou fixos cadastrados errados).
            else if (incomingNumber.length() == 13) {
                String numberWithoutNine = incomingNumber.substring(0, 4) + incomingNumber.substring(5);
                optContact = contactRepository.findByCompanyAndPhoneNumber(company, numberWithoutNine);
                
                if (optContact.isPresent()) {
                    log.debug("Contato encontrado removendo 9º dígito: {} -> {}", incomingNumber, numberWithoutNine);
                }
            }
        }

        // 3. Se achou, retorna. Se não, cria NOVO (Padronizando para salvar COM 9 se for celular BR)
        return optContact.orElseGet(() -> {
            log.info("Contato não encontrado. Criando novo para: {}", incomingNumber);
            Contact newContact = new Contact();
            newContact.setCompany(company);
            newContact.setName(profileName);
            
            // PADRONIZAÇÃO NA CRIAÇÃO:
            // Se chegou sem o 9 (12 dígitos) e é Brasil, salvamos COM o 9 para evitar duplicidade futura.
            String finalNumber = incomingNumber;
            if (incomingNumber.startsWith("55") && incomingNumber.length() == 12) {
                finalNumber = incomingNumber.substring(0, 4) + "9" + incomingNumber.substring(4);
                log.info("Padronizando novo contato BR para formato 13 dígitos: {}", finalNumber);
            }
            
            newContact.setPhoneNumber(finalNumber);
            return contactRepository.save(newContact);
        });
    }

    // private Company findCompanyByMetaPhoneNumberId(String metaPhoneNumberId) { // Renomeado
    //     if (metaPhoneNumberId == null || metaPhoneNumberId.isBlank()) {
    //         log.trace("findCompanyByMetaPhoneNumberId chamado com ID nulo ou vazio.");
    //         return null;
    //     }
    //     log.debug("Buscando empresa por metaPrimaryPhoneNumberId ou metaPhoneNumberId: {}", metaPhoneNumberId);
    //     // Primeiro tenta pelo campo primário, depois por um campo genérico (se você tiver múltiplos números por empresa)
    //     Optional<Company> company = companyRepository.findByMetaPrimaryPhoneNumberId(metaPhoneNumberId);
    //     if (company.isPresent()) {
    //         return company.get();
    //     }
    //     // Se não, você poderia ter uma tabela separada CompanyPhoneNumber para mapear múltiplos números para uma Company
    //     // ou se o metaPhoneNumberId na Company for uma lista. Por agora, simplificado.
    //     // Se sua entidade Company tiver um campo metaPhoneNumberId que pode não ser o primário, use-o:
    //     // return companyRepository.findBySomeOtherMetaPhoneNumberIdField(metaPhoneNumberId).orElse(null);
    //     log.warn("Nenhuma empresa encontrada com metaPrimaryPhoneNumberId: {}", metaPhoneNumberId);
    //     return null;
    // }

    @SuppressWarnings("static-access")
    private void extractMessageContentAndMetadata(JsonNode messageNode, WhatsAppMessageLog log) throws JsonProcessingException {
        
        String type = log.getMessageType().toLowerCase();
        log.setContent(null);
        log.setMetadata(null);
        Map<String, Object> metaMap = new HashMap<>();

        switch (type) {
            case "text":
                log.setContent(messageNode.path("text").path("body").asText());
                break;
            case "image":
            case "video":
            case "audio":
            case "document":
                String mediaId = messageNode.path(type).path("id").asText();
                String caption = messageNode.path(type).path("caption").asText(null);
                log.setContent(mediaId);
                metaMap.put("mime_type", messageNode.path(type).path("mime_type").asText(null));
                if(caption != null) metaMap.put("caption", caption);
                if("document".equals(type)) metaMap.put("filename", messageNode.path(type).path("filename").asText(null));
                break;
            case "interactive":
                JsonNode interactiveNode = messageNode.path("interactive");
                String interactiveType = interactiveNode.path("type").asText();
                log.setMetadata(objectMapper.writeValueAsString(interactiveNode)); // Salva toda a estrutura interativa como metadados

                switch (interactiveType) {
                    case "button_reply":
                        JsonNode buttonReplyNode = interactiveNode.path("button_reply");
                        String buttonId = buttonReplyNode.path("id").asText();
                        String buttonTitle = buttonReplyNode.path("title").asText();
                        log.setContent(buttonId); // ID do botão clicado é o conteúdo principal
                        this.log.info("  -> Resposta de Botão: ID='{}', Título='{}'", buttonId, buttonTitle);
                        break;
                    case "list_reply":
                        JsonNode listReplyNode = interactiveNode.path("list_reply");
                        String listRowId = listReplyNode.path("id").asText();
                        String listRowTitle = listReplyNode.path("title").asText();
                        String listRowDescription = listReplyNode.path("description").asText(null);
                        log.setContent(listRowId); // ID da linha da lista é o conteúdo principal
                        this.log.info("  -> Resposta de Lista: ID='{}', Título='{}', Descrição='{}'",
                                 listRowId, listRowTitle, listRowDescription);
                        break;
                    case "nfm_reply": // Resposta de um Flow
                        JsonNode nfmReplyNode = interactiveNode.path("nfm_reply");
                        String flowReplyBody = nfmReplyNode.path("body").asText();
                        String responseJsonString = nfmReplyNode.path("response_json").asText(null);
                        
                        this.log.info("  Conteúdo (Resposta Final de Flow): Botão='{}'", flowReplyBody);

                        if (responseJsonString != null) {
                            try {
                                JsonNode responseJson = objectMapper.readTree(responseJsonString);
                                String flowToken = responseJson.path("flow_token").asText("N/A");
                                String metaFlowId = responseJson.path("flow_id").asText("N/A");
                                JsonNode flowData = responseJson.path("flow_data");

                                this.log.info("  Dados da Resposta Final do Flow: Token='{}', FlowID='{}', Dados='{}'",
                                        flowToken, metaFlowId, flowData.toString());
                                
                                // Salva os dados no log principal
                                log.setContent("Resposta Final do Flow: " + flowReplyBody);
                                log.setMetadata(responseJsonString); // Salva o JSON completo como metadados
                            } catch (JsonProcessingException e) {
                                this.log.error("  Erro ao fazer parse do 'response_json' do nfm_reply: {}", responseJsonString, e);
                                log.setContent("Resposta Final de Flow (JSON inválido)");
                                log.setMetadata(responseJsonString);
                            }
                        } else {
                            this.log.warn("  nfm_reply recebido sem 'response_json'.");
                            log.setContent("Resposta Final de Flow (sem dados)");
                        }
                        break;
                    default:
                        log.setContent("Tipo interativo desconhecido: " + interactiveType);
                        this.log.info("  -> Tipo de resposta interativa não tratada: {}", interactiveType);
                        break;
                }
                break;
            case "reaction":
                String reactedWamid = messageNode.path("reaction").path("message_id").asText();
                String emoji = messageNode.path("reaction").path("emoji").asText(null);
                log.setContent(emoji);
                metaMap.put("reacted_wamid", reactedWamid);
                break;
            case "location":
                log.setContent(String.format("Lat: %s, Lon: %s",
                        messageNode.path("location").path("latitude").asText(),
                        messageNode.path("location").path("longitude").asText()));
                metaMap.put("name", messageNode.path("location").path("name").asText(null));
                metaMap.put("address", messageNode.path("location").path("address").asText(null));
                metaMap.put("url", messageNode.path("location").path("url").asText(null));
                break;
            case "contacts":
                log.setContent("Contato(s) Recebido(s)");
                log.setMetadata(objectMapper.writeValueAsString(messageNode.path("contacts")));
                break;
            case "unsupported":
                log.setContent("Mensagem não suportada recebida.");
                log.setMetadata(messageNode.toString());
                break;
            case "system":
                log.setContent(messageNode.path("system").path("body").asText());
                metaMap.put("system_type", messageNode.path("system").path("type").asText());
                break;
            default:
                log.setContent("Tipo desconhecido: " + type);
                log.setMetadata(messageNode.toString());
                break;
        }

        if (!metaMap.isEmpty() && log.getMetadata() == null) {
            log.setMetadata(objectMapper.writeValueAsString(metaMap));
        }
    }

    private void handleAccountUpdateField(JsonNode valueNode, String wabaId) {
        String event = valueNode.path("event").asText(null);
        if ("VOLUME_BASED_PRICING_TIER_UPDATE".equals(event)) {
            JsonNode tierInfo = valueNode.path("volume_tier_info");
            String region = tierInfo.path("region").asText();
            String categoryStr = tierInfo.path("pricing_category").asText();
            String tier = tierInfo.path("tier").asText();
            String effectiveMonthStr = tierInfo.path("effective_month").asText(); // "2025-11"

            log.info("WEBHOOK DE NÍVEL DE VOLUME: WABA {} atingiu o nível '{}' para {} em {}.",
                    wabaId, tier, categoryStr, region);

            try {
                TemplateCategory category = TemplateCategory.valueOf(categoryStr.toUpperCase());
                YearMonth effectiveMonth = YearMonth.parse(effectiveMonthStr);

                // Lógica de Upsert
                CompanyTierStatus tierStatus = companyTierStatusRepository
                    .findByWabaIdAndCategoryAndEffectiveMonth(wabaId, category, effectiveMonth)
                    .orElseGet(CompanyTierStatus::new);
                
                tierStatus.setWabaId(wabaId);
                tierStatus.setCategory(category);
                tierStatus.setEffectiveMonth(effectiveMonth);
                tierStatus.setCurrentTier(tier);
                tierStatus.setRegion(region);

                companyTierStatusRepository.save(tierStatus);
                log.info("Status do nível de volume para WABA {} salvo/atualizado no banco.", wabaId);

                // Disparar alerta para a equipe de billing
                adminNotificationService.notifyCallbackFailure( // Reutilizando, idealmente seria um método notifyBillingEvent
                    "Atualização de Nível de Volume",
                    String.format("WABA ID %s atingiu o nível %s para a categoria %s na região %s para o mês %s.",
                                wabaId, tier, category, region, effectiveMonth)
                );

            } catch (Exception e) {
                log.error("Erro ao processar webhook de atualização de nível de volume para WABA {}: {}", wabaId, e.getMessage(), e);
            }
        }
    }

    private void processAndSaveFlowData(JsonNode interactiveNode, Company company, Contact contact, String senderWaId) {
        
        JsonNode nfmReplyNode = interactiveNode.path("nfm_reply");
        String responseJsonString = nfmReplyNode.path("response_json").asText(null);

        if (responseJsonString == null || responseJsonString.isBlank()) {
            log.warn("Tentativa de salvar FlowData falhou: response_json vazio.");
            return;
        }

        try {
            JsonNode responseJson = objectMapper.readTree(responseJsonString);

            // 1. Tenta identificar o Flow pelo TOKEN (Estratégia Recomendada)
            // O flow_token vem automaticamente no response_json se foi enviado no disparo
            String flowToken = responseJson.path("flow_token").asText(null);

            // 2. Tenta identificar pelo ID explícito no payload (Fallback)
            // Caso você tenha mantido o campo no JSON, mas evite usar placeholder
            String payloadFlowId = responseJson.path("flow_id").asText(null);
            
            Flow flow = null;
            // Lógica de Busca do Flow
            if (flowToken != null && !flowToken.isBlank() && !"unused".equalsIgnoreCase(flowToken)) {
                // Tenta achar pelo ID numérico do nosso banco (se você mandou o ID do banco no token)
                try {
                    Long dbId = Long.parseLong(flowToken);
                    flow = flowRepository.findById(dbId).orElse(null);
                } catch (NumberFormatException e) {
                    // Se não for número, tenta achar pelo Meta Flow ID (se você mandou o ID da Meta no token)
                    flow = flowRepository.findByMetaFlowId(flowToken).orElse(null);
                }
            }

            // Se não achou pelo token, tenta pelo payload explícito (se não for o placeholder)
            if (flow == null && payloadFlowId != null && !"FLOW_ID_PLACEHOLDER".equals(payloadFlowId)) {
                flow = flowRepository.findByMetaFlowId(payloadFlowId).orElse(null);
            }

            if (flow == null) {
                log.warn("FlowData recebido de {} mas não foi possível identificar o Flow. Token: '{}', PayloadID: '{}'", 
                         senderWaId, flowToken, payloadFlowId);
            } else {
                log.debug("Flow identificado com sucesso: {} (ID: {})", flow.getName(), flow.getId());
            }

            FlowData flowData = new FlowData();
            flowData.setCompany(company);
            flowData.setContact(contact);
            flowData.setFlow(flow); // Pode ser nulo se não acharmos pelo ID
            flowData.setSenderWaId(senderWaId);
            flowData.setDecryptedJsonResponse(responseJsonString); // Salva o JSON bruto da resposta
            
            // Salva no banco
            FlowData savedFlowData = flowDataRepository.save(flowData);
            log.info("FlowData salvo com sucesso. ID: {}", savedFlowData.getId());

            // Dispara o callback específico de Flow Data para avisar o sistema externo/cliente
            if (company != null) {
                callbackService.sendFlowDataCallback(company.getId(), savedFlowData.getId());
            }

        } catch (JsonProcessingException e) {
            log.error("Erro ao fazer parse do response_json ao salvar FlowData: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Erro genérico ao salvar FlowData: {}", e.getMessage(), e);
        }
    }

    /**
     * Busca o Canal (Número de Telefone) baseado no ID da Meta vindo no webhook.
     * A partir do Canal, temos acesso à Empresa.
     */
    private WhatsAppPhoneNumber findChannelByMetaId(String metaPhoneNumberId) {
        if (metaPhoneNumberId == null || metaPhoneNumberId.isBlank()) {
            log.trace("findChannelByMetaId chamado com ID nulo ou vazio.");
            return null;
        }
        
        return phoneNumberRepository.findByPhoneNumberId(metaPhoneNumberId)
                .orElseGet(() -> {
                    log.warn("Nenhum canal encontrado com phoneNumberId: {}", metaPhoneNumberId);
                    return null;
                });
    }
}
