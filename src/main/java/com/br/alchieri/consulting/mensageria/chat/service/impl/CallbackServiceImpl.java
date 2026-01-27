package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.callback.CampaignStatusCallbackPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.FlowDataCallbackPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.FlowStatusCallbackPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.IncomingMessagePayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.InternalCallbackPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.MessageStatusPayload;
import com.br.alchieri.consulting.mensageria.chat.dto.callback.TemplateStatusCallbackPayload;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.repository.ClientTemplateRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowDataRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.ScheduledCampaignRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.WhatsAppMessageLogRepository;
import com.br.alchieri.consulting.mensageria.chat.service.CallbackService;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.service.AdminNotificationService;
import com.br.alchieri.consulting.mensageria.service.InternalEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j // Lombok para logger
public class CallbackServiceImpl implements CallbackService {

    // Usar WebClient padrão, pois a URL base varia e não precisamos do token da Meta
    private final WebClient.Builder webClientBuilder;

    private final AdminNotificationService adminNotificationService;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final ScheduledCampaignRepository campaignRepository;
    private final CompanyRepository companyRepository;
    private final ClientTemplateRepository clientTemplateRepository;
    private final FlowRepository flowRepository;
    private final ObjectMapper objectMapper;
    private final FlowDataRepository flowDataRepository;

    private final InternalEventService internalEventService;

    // Configuração de retentativa (opcional)
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    // @Value("${app.internal.api-key}")
    // private String internalApiKey;

    // // URL completa para o endpoint de callback interno (pode vir do .properties)
    // // Se rodando na mesma máquina/cluster, pode ser o nome do serviço interno.
    // // Assumindo que pode ser chamado externamente por agora.
    // @Value("${app.internal.callback-url}")
    // private String internalCallbackUrl; // Ex: https://mensageriaapi.alchiericonsulting.com/api/v1/internal-callbacks/events


    @Async("taskExecutor")
    @Override
    public void sendIncomingMessageCallback(Long companyId, Long messageLogId) {

        Company company = companyRepository.findById(companyId).orElse(null);
        WhatsAppMessageLog messageLog = messageLogRepository.findById(messageLogId).orElse(null);
        if (company == null || messageLog == null) return;
        IncomingMessagePayload clientPayload = IncomingMessagePayload.fromLog(messageLog);
        InternalCallbackPayload<IncomingMessagePayload> internalPayload = InternalCallbackPayload.<IncomingMessagePayload>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("INCOMING_MESSAGE")
                .eventTimestamp(LocalDateTime.now())
                .companyId(company.getId())
                .userId(messageLog.getUser() != null ? messageLog.getUser().getId() : null)
                .data(clientPayload)
                .build();

        sendToInternalEndpoint(internalPayload);

        if (company.getGeneralCallbackUrl() != null && !company.getGeneralCallbackUrl().isBlank()) {
            sendToClientEndpoint(company.getGeneralCallbackUrl(), clientPayload,
                String.format("Mensagem Recebida para Empresa %d, De %s", company.getId(), messageLog.getSenderPhoneNumber()));
        }
    }

    @Async("taskExecutor")
    @Override
    public void sendStatusCallback(Long companyId, Long messageLogId) {

        Company company = companyRepository.findById(companyId).orElse(null);
        WhatsAppMessageLog messageLog = messageLogRepository.findById(messageLogId).orElse(null);
        if (company == null || messageLog == null) return;
        MessageStatusPayload clientPayload = MessageStatusPayload.fromLog(messageLog);
        InternalCallbackPayload<MessageStatusPayload> internalPayload = InternalCallbackPayload.<MessageStatusPayload>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("MESSAGE_STATUS")
                .eventTimestamp(LocalDateTime.now())
                .companyId(company.getId())
                .userId(messageLog.getUser() != null ? messageLog.getUser().getId() : null)
                .data(clientPayload)
                .build();

        sendToInternalEndpoint(internalPayload);

        if (company.getGeneralCallbackUrl() != null && !company.getGeneralCallbackUrl().isBlank()) {
            sendToClientEndpoint(company.getGeneralCallbackUrl(), clientPayload,
                String.format("Status de Mensagem para Empresa %d, WAMID %s", company.getId(), messageLog.getWamid()),
                messageLog);
        }
    }

    @Async("taskExecutor")
    @Override
    public void sendTemplateStatusCallback(Long companyId, Long clientTemplateId) {
        
        Company company = companyRepository.findById(companyId).orElse(null);
        ClientTemplate clientTemplate = clientTemplateRepository.findById(clientTemplateId).orElse(null);
        if (company == null || clientTemplate == null) return;

        String targetCallbackUrl = company.getTemplateStatusCallbackUrl() != null ?
                                   company.getTemplateStatusCallbackUrl() : company.getGeneralCallbackUrl();
        if (targetCallbackUrl == null || targetCallbackUrl.isBlank()) return;

        log.info("Enviando callback de status de template ({}) para Empresa ID {}, Template '{}'",
                clientTemplate.getStatus(), company.getId(), clientTemplate.getTemplateName());

        updateCallbackAttempt(clientTemplate, "PENDING");

        TemplateStatusCallbackPayload clientPayload = TemplateStatusCallbackPayload.fromClientTemplate(clientTemplate);
        
        sendToClientEndpoint(targetCallbackUrl, clientPayload,
            String.format("Status de Template para Empresa %d, Template '%s'", company.getId(), clientTemplate.getTemplateName()),
            clientTemplate);
    }

    @Async("taskExecutor")
    @Override
    public void sendCampaignStatusCallback(Long companyId, Long campaignId) {

        Company company = companyRepository.findById(companyId).orElse(null);
        ScheduledCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (company == null || campaign == null) return;
        CampaignStatusCallbackPayload clientPayload = CampaignStatusCallbackPayload.fromEntity(campaign);
        InternalCallbackPayload<CampaignStatusCallbackPayload> internalPayload = InternalCallbackPayload.<CampaignStatusCallbackPayload>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CAMPAIGN_STATUS")
                .eventTimestamp(LocalDateTime.now())
                .companyId(company.getId())
                .data(clientPayload)
                .build();

        sendToInternalEndpoint(internalPayload);

        String clientCallbackUrl = company.getGeneralCallbackUrl();
        if (clientCallbackUrl != null && !clientCallbackUrl.isBlank()) {
            sendToClientEndpoint(clientCallbackUrl, clientPayload,
                String.format("Status de Campanha para Empresa %d, Campanha ID %d", company.getId(), campaign.getId()),
                campaign);
        }
    }
    
    @Async("taskExecutor")
    @Override
    public void sendFlowStatusCallback(Long companyId, Long flowId) {

        Company company = companyRepository.findById(companyId).orElse(null);
        Flow flow = flowRepository.findById(flowId).orElse(null);
        if (company == null || flow == null) return;
        String targetCallbackUrl = company.getGeneralCallbackUrl(); // Assumindo URL geral para Flows
        if (targetCallbackUrl == null || targetCallbackUrl.isBlank()) {
            log.debug("Callback de status de Flow pulado para Flow ID {}: Empresa sem URL de callback.", flow.getId());
            return;
        }

        log.info("Enviando callback de status de Flow ({}) para {} (Empresa ID {}, Flow ID {})",
                flow.getStatus(), targetCallbackUrl, company.getId(), flow.getId());

        updateCallbackAttempt(flow, "PENDING"); // Marcar tentativa

        FlowStatusCallbackPayload payload = FlowStatusCallbackPayload.fromEntity(flow);
        sendToClientEndpoint(targetCallbackUrl, payload,
            String.format("Status de Flow para Empresa %d, Flow ID %d", company.getId(), flow.getId()),
            flow);
    }

    @Async("taskExecutor")
    @Override
    public void sendFlowDataCallback(Long companyId, Long flowDataId) {

        Company company = companyRepository.findById(companyId).orElse(null);
        FlowData flowData = flowDataRepository.findById(flowDataId).orElse(null);
        if (company == null || flowData == null) return;
        
        String targetCallbackUrl = company.getGeneralCallbackUrl();
        if (targetCallbackUrl == null || targetCallbackUrl.isBlank()) {
            log.debug("Callback de dados de Flow pulado para FlowData ID {}: Empresa sem URL de callback.", flowData.getId());
            return;
        }

        log.info("Enviando callback de DADOS DE FLOW para {} (Empresa ID {}, FlowData ID {})",
                targetCallbackUrl, company.getId(), flowData.getId());

        FlowDataCallbackPayload clientPayload = FlowDataCallbackPayload.fromEntity(flowData, objectMapper);
        InternalCallbackPayload<FlowDataCallbackPayload> internalPayload = buildInternalPayload("FLOW_DATA", company.getId(), null, clientPayload);
        
        sendToInternalEndpoint(internalPayload);
        
        if (targetCallbackUrl != null && !targetCallbackUrl.isBlank()) {
            sendToClientEndpoint(
                targetCallbackUrl,
                clientPayload,
                String.format("Dados de Flow para Empresa %d, FlowData ID %d", company.getId(), flowData.getId()),
                flowData
            );
        }
    }

    // --- Métodos Helper Privados ---

    private void sendToInternalEndpoint(InternalCallbackPayload<?> payload) {
        try {
            log.debug("Delegando evento interno para InternalEventService localmente (sem HTTP).");
            internalEventService.processInternalEvent(payload);
        } catch (Exception e) {
             log.error("FALHA CRÍTICA AO PROCESSAR EVENTO INTERNO LOCALMENTE! Evento Tipo: {}, Empresa ID: {}. Erro: {}",
                        payload.getEventType(), payload.getCompanyId(), e.getMessage(), e);
        }
    }

    private void sendToClientEndpoint(String clientCallbackUrl, Object clientPayload, String logContext, Object originalEntity) {
        if (originalEntity != null) {
            updateCallbackAttempt(originalEntity, "PENDING");
        }
        WebClient client = webClientBuilder.build();
        client.post()
                .uri(clientCallbackUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(clientPayload))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(responseBody -> {
                    log.info("Callback para cliente enviado com sucesso. Contexto: [{}]. Resposta: {}", logContext, responseBody);
                    if (originalEntity != null) updateCallbackAttempt(originalEntity, "SUCCESS");
                })
                .doOnError(error -> log.warn("Falha ao enviar callback para cliente. Contexto: [{}]. Erro: {}", logContext, error.getMessage()))
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                           .filter(throwable -> throwable instanceof WebClientResponseException && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError())
                           .doBeforeRetry(retrySignal -> log.warn("Retentativa {} para callback cliente. Contexto: [{}]. Erro: {}",
                                        retrySignal.totalRetries() + 1, logContext, retrySignal.failure().getMessage())))
                .onErrorResume(error -> {
                    log.error("FALHA FINAL ao enviar callback para cliente. Contexto: [{}]. Erro: {}", logContext, error.getMessage());
                    adminNotificationService.notifyCallbackFailure(
                        "Falha Crítica de Callback - Cliente Externo",
                        String.format("Não foi possível enviar callback para a URL %s. Contexto: [%s]. Erro: %s",
                                      clientCallbackUrl, logContext, error.getMessage())
                    );
                    if (originalEntity != null) updateCallbackAttempt(originalEntity, "FAILED_FINAL");
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
    
    private void sendToClientEndpoint(String clientCallbackUrl, Object clientPayload, String logContext) {
        // Versão que não atualiza o banco (ex: para mensagens recebidas)
        sendToClientEndpoint(clientCallbackUrl, clientPayload, logContext, null);
    }

    private void updateCallbackAttempt(Object entity, String status) {
        try {
            if (entity instanceof WhatsAppMessageLog log) {
                messageLogRepository.findById(log.getId()).ifPresent(dbLog -> {
                    dbLog.setLastCallbackAttempt(LocalDateTime.now());
                    dbLog.setLastCallbackStatus(status);
                    messageLogRepository.save(dbLog);
                });
            } else if (entity instanceof ScheduledCampaign campaign) {
                campaignRepository.findById(campaign.getId()).ifPresent(dbCampaign -> {
                    dbCampaign.setLastCallbackAttempt(LocalDateTime.now());
                    dbCampaign.setLastCallbackStatus(status);
                    campaignRepository.save(dbCampaign);
                });
            } else if (entity instanceof ClientTemplate template) {
                log.debug("Rastreamento de callback para ClientTemplate ainda não implementado na entidade.");
            } else if (entity instanceof Flow flow) {
                flowRepository.findById(flow.getId()).ifPresent(dbFlow -> {
                    dbFlow.setLastCallbackAttempt(LocalDateTime.now());
                    dbFlow.setLastCallbackStatus(status);
                    flowRepository.save(dbFlow);
                });
            } else if (entity instanceof FlowData flowData && flowData.getId() != null) {
                flowDataRepository.findById(flowData.getId()).ifPresent(dbFlowData -> {
                    dbFlowData.setLastCallbackAttempt(LocalDateTime.now());
                    dbFlowData.setLastCallbackStatus(status);
                    flowDataRepository.save(dbFlowData);
                });
            } else {
                log.warn("Tentativa de atualizar status de callback para entidade desconhecida: {}", entity.getClass().getName());
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar o status do callback para a entidade {}: {}", entity, e.getMessage());
        }
    }

    private <T> InternalCallbackPayload<T> buildInternalPayload(String eventType, Long companyId, Long userId, T data) {
        return InternalCallbackPayload.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .companyId(companyId)
                .userId(userId)
                .data(data)
                .build();
    }
}
