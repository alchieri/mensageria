package com.br.alchieri.consulting.mensageria.chat.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.meta.InteractivePayload;
import com.br.alchieri.consulting.mensageria.chat.dto.meta.WhatsAppCloudApiRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.meta.WhatsAppTemplatePayload;
import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMultiProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateComponentRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.TemplateParameterRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.MessageStatusResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.br.alchieri.consulting.mensageria.chat.repository.ContactRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.FlowRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.MediaUploadRepository;
import com.br.alchieri.consulting.mensageria.chat.repository.WhatsAppMessageLogRepository;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.chat.util.TemplateParameterGenerator;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class WhatsAppCloudApiServiceImpl implements WhatsAppCloudApiService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppCloudApiServiceImpl.class);

    private final WebClient.Builder webClientBuilder;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final BillingService billingService;
    private final TemplateParameterGenerator parameterGenerator;
    private final ObjectMapper objectMapper;
    private final MediaUploadRepository mediaUploadRepository;
    private final S3Template s3Template;
    private final FlowRepository flowRepository;

    // URL base global, o token será específico do cliente
    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String bspSystemUserAccessToken;

    @Value("${aws.s3.media-bucket-name}")
    private String s3MediaBucketName;

    @Override
    public Mono<Void> sendFromQueue(OutgoingMessageRequest queueRequest, User user) {
        
        Company company = getCompanyFromUser(user);

        if (!billingService.canCompanySendMessages(company, 1)) {
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }

        Mono<WhatsAppCloudApiRequest> metaRequestMono;
        String messageType = queueRequest.getMessageType();
        String contentReference;

        if ("TEXT".equals(messageType)) {
            metaRequestMono = Mono.just(buildTextMetaRequest(queueRequest.getTextRequest()));
            contentReference = queueRequest.getTextRequest().getMessage();
        } else if ("TEMPLATE".equals(messageType)) {
            metaRequestMono = buildTemplateMetaRequest(queueRequest.getTemplateRequest(), company, user);
            contentReference = queueRequest.getTemplateRequest().getTemplateName();
        } else if ("INTERACTIVE_FLOW".equals(messageType)) {
            metaRequestMono = buildInteractiveFlowMetaRequest(queueRequest.getInteractiveFlowRequest());
            contentReference = queueRequest.getInteractiveFlowRequest().getFlowName(); // Usa o nome amigável para o log
        } else {
            return Mono.error(new BusinessException("Tipo de mensagem desconhecido na fila: " + messageType));
        }

        return metaRequestMono.flatMap(metaRequest ->
            executeSendMessage(metaRequest, user, company, messageType, contentReference, queueRequest.getScheduledMessageId())
        );
    }

    @Override
    public Mono<Void> sendTextMessage(SendTextMessageRequest request, User user) {
        
        Company company = getCompanyFromUser(user);
        if (!billingService.canCompanySendMessages(company, 1)) {
            logger.warn("Empresa ID {}: Limite de envio de mensagens excedido.", company.getId());
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }
        WhatsAppCloudApiRequest metaRequest = buildTextMetaRequest(request);
        return executeSendMessage(metaRequest, user, company, "TEXT", request.getMessage(), null);
    }

    public Mono<Void> sendTemplateMessage(SendTemplateMessageRequest request, User user) {
        
        return sendTemplateMessage(request, user, null); 
    }

    @Override
    public Mono<Void> sendTemplateMessage(SendTemplateMessageRequest request, User user, Long scheduledMessageId) {
        
        Company company = getCompanyFromUser(user);
        if (!billingService.canCompanySendMessages(company, 1)) {
            logger.warn("Empresa ID {}: Limite de envio de mensagens excedido.", company.getId());
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }
        Mono<WhatsAppCloudApiRequest> metaRequestMono = buildTemplateMetaRequest(request, company, user);
        return metaRequestMono.flatMap(metaRequest -> {
            return executeSendMessage(metaRequest, user, company, "TEMPLATE", request.getTemplateName(), scheduledMessageId);
        });
    }

    @Override
    public Mono<String> uploadMedia(MultipartFile file, String messagingProduct, User user) {
        Company company = getCompanyFromUser(user);
        
        if (file == null || file.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Arquivo não pode ser vazio para upload."));
        }

        validateFileSize(file);

        // 1. Gerar uma chave de objeto única para o S3
        String objectKey = String.format("company-%d/user-%d/%s-%s",
                                         company.getId(),
                                         user.getId(),
                                         UUID.randomUUID().toString(),
                                         file.getOriginalFilename());

        logger.info("Empresa ID {}: Preparando para fazer upload da mídia '{}' para o S3 com a chave: {}",
                    company.getId(), file.getOriginalFilename(), objectKey);

        // 2. Fazer upload para S3
        Mono<Void> s3UploadMono = Mono.fromFuture(() -> {
            try {
                // O S3Template lida com o InputStream
                s3Template.upload(s3MediaBucketName, objectKey, file.getInputStream());
                return CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(new RuntimeException("Falha ao ler o arquivo para o S3.", e));
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();


        // 3. Fazer upload para a Meta (pode ser em paralelo)
        Mono<String> metaUploadMono = uploadToMeta(file, messagingProduct, user);

        // 4. Combinar os resultados e salvar no banco
        return Mono.zip(s3UploadMono.then(Mono.just(objectKey)), metaUploadMono)
            .flatMap(tuple -> {
                String savedObjectKey = tuple.getT1();
                String metaMediaId = tuple.getT2();

                return Mono.fromCallable(() -> {
                    MediaUpload mediaUpload = new MediaUpload();
                    mediaUpload.setCompany(company);
                    mediaUpload.setUploadedBy(user);
                    mediaUpload.setMetaMediaId(metaMediaId);
                    mediaUpload.setOriginalFilename(file.getOriginalFilename());
                    mediaUpload.setContentType(file.getContentType());
                    mediaUpload.setFileSize(file.getSize());
                    mediaUpload.setS3BucketName(s3MediaBucketName); // Salva referência do S3
                    mediaUpload.setS3ObjectKey(savedObjectKey);   // Salva referência do S3

                    mediaUploadRepository.save(mediaUpload);
                    logger.info("Empresa ID {}: Mídia salva no S3 ({}) e na Meta ({}) com sucesso. Registro no BD criado.",
                                company.getId(), savedObjectKey, metaMediaId);
                    return metaMediaId; // Retorna o mediaId para o cliente
                }).subscribeOn(Schedulers.boundedElastic());
            });
    }

    @Override
    public Mono<MessageStatusResponse> getMessageStatusByWamid(String wamid) {
        User currentUser = getCurrentUser();

        return Mono.defer(() -> {
            Optional<WhatsAppMessageLog> optLog = messageLogRepository.findByWamid(wamid);
            if (optLog.isEmpty()) {
                return Mono.empty();
            }
            WhatsAppMessageLog log = optLog.get();

            if (!currentUser.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
                (log.getCompany() == null || !log.getCompany().getId().equals(currentUser.getCompany().getId()))) {
                logger.warn("Usuário (ID {}) tentou acessar status de mensagem (WAMID {}) de outra empresa (ID {}).",
                        currentUser.getId(), wamid, log.getCompany() != null ? log.getCompany().getId() : "N/A");
                return Mono.error(new AccessDeniedException("Acesso negado ao status desta mensagem."));
            }
            return Mono.just(MessageStatusResponse.fromLog(log));
        });
    }

    @Override
    public Mono<MessageStatusResponse> getMessageStatusByLogId(Long logId) {
        User currentUser = getCurrentUser();

        return Mono.defer(() -> {
            Optional<WhatsAppMessageLog> optLog = messageLogRepository.findByIdWithCompany(logId); // <<< Usar método com FETCH
            if (optLog.isEmpty()) return Mono.empty();
            
            WhatsAppMessageLog log = optLog.get();
            // Lógica de permissão é a mesma
            if (!currentUser.getRoles().contains(Role.ROLE_BSP_ADMIN) &&
                (log.getCompany() == null || !log.getCompany().getId().equals(currentUser.getCompany().getId()))) {
                return Mono.error(new AccessDeniedException("Acesso negado a este log de mensagem."));
            }
            return Mono.just(MessageStatusResponse.fromLog(log));
        });
    }

    @Override
    public Mono<Void> sendInteractiveFlowMessage(SendInteractiveFlowMessageRequest request, User user) {
        
        Company company = getCompanyFromUser(user);
        if (!billingService.canCompanySendMessages(company, 1)) {
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }
        return buildInteractiveFlowMetaRequest(request)
                .flatMap(metaRequest -> 
                    executeSendMessage(metaRequest, user, company, "INTERACTIVE_FLOW", request.getFlowName(), null)
                );
    }

    @Override
    public Mono<Void> sendProductMessage(SendProductMessageRequest request, User currentUser) {
        
        Company company = getCompanyFromUser(currentUser);

        if (!billingService.canCompanySendMessages(company, 1)) {
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }

        // Constrói o request de forma reativa (caso precise buscar algo no futuro)
        return Mono.fromCallable(() -> buildSingleProductMetaRequest(request, company))
                .flatMap(metaRequest -> 
                    executeSendMessage(metaRequest, currentUser, company, "PRODUCT", "Produto: " + request.getProductRetailerId(), null)
                );
    }

    @Override
    public Mono<Void> sendMultiProductMessage(SendMultiProductMessageRequest request, User currentUser) {
        
        Company company = getCompanyFromUser(currentUser);

        if (!billingService.canCompanySendMessages(company, 1)) {
            return Mono.error(new BusinessException("Limite de envio de mensagens excedido."));
        }

        return Mono.fromCallable(() -> buildMultiProductMetaRequest(request, company))
                .flatMap(metaRequest -> 
                    executeSendMessage(metaRequest, currentUser, company, "PRODUCT_LIST", "Lista de Produtos: " + request.getHeaderText(), null)
                );
    }

    // --- Métodos Helper Privados ---

    private void validateFileSize(MultipartFile file) {
        String contentType = file.getContentType();
        long size = file.getSize();
        
        if (contentType == null) return; // Deixa passar se não tiver content type (arriscado, mas evita bloqueio indevido)

        if (contentType.startsWith("image/")) {
            // Limite de 5MB para Imagens
            if (size > 5 * 1024 * 1024) {
                throw new BusinessException("A imagem excede o limite máximo permitido de 5MB pela API do WhatsApp.");
            }
        } else if (contentType.startsWith("video/")) {
            // Limite de 16MB para Vídeos
            if (size > 16 * 1024 * 1024) {
                throw new BusinessException("O vídeo excede o limite máximo permitido de 16MB pela API do WhatsApp.");
            }
        } else if (contentType.startsWith("audio/")) {
            // Limite de 16MB para Áudios
            if (size > 16 * 1024 * 1024) {
                throw new BusinessException("O áudio excede o limite máximo permitido de 16MB pela API do WhatsApp.");
            }
        } else if (contentType.equals("image/webp")) {
             // Limite de 100KB/500KB para Stickers (simplificado para 500KB para cobrir ambos)
             if (size > 500 * 1024) {
                throw new BusinessException("O sticker excede o limite máximo permitido de 500KB.");
             }
        } else {
            // Limite de 100MB para Documentos (Limite geral da API)
            if (size > 100 * 1024 * 1024) {
                throw new BusinessException("O documento excede o limite máximo permitido de 100MB.");
            }
        }
    }

    // Método helper privado para o upload para a Meta (código que já tínhamos)
    @SuppressWarnings("null")
    private Mono<String> uploadToMeta(MultipartFile file, String messagingProduct, User user) {
        
        Company company = getCompanyFromUser(user);
        WebClient bspWebClient = getBspWebClient();
        String uploaderPhoneNumberId = getCompanyPhoneNumberId(company);
        String endpoint = "/" + uploaderPhoneNumberId + "/media";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("messaging_product", messagingProduct);
        try {
            builder.part("file", new InputStreamResource(file.getInputStream()))
                   .filename(file.getOriginalFilename())
                   .contentType(MediaType.parseMediaType(file.getContentType()));
        } catch (IOException e) {
            return Mono.error(new RuntimeException("Erro ao ler arquivo para upload na Meta.", e));
        }

        return bspWebClient.post().uri(endpoint).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build())).retrieve()
                .bodyToMono(JsonNode.class)
                .map(responseNode -> {
                    String mediaId = responseNode.path("id").asText(null);
                    if (mediaId == null) throw new RuntimeException("Falha ao obter ID da mídia da Meta.");
                    return mediaId;
                });
    }

    private Mono<Void> executeSendMessage(WhatsAppCloudApiRequest metaRequest, User user, Company company, String messageType, String contentReference, Long scheduledMessageId) {
        
        String senderPhoneNumberId = getCompanyPhoneNumberId(company);
        WebClient bspWebClient = getBspWebClient();
        String endpoint = "/" + senderPhoneNumberId + "/messages";
        String recipientPhoneNumber = metaRequest.getTo();

        try {
            logger.info("Empresa ID {}: Enviando payload para Meta API: {}", company.getId(), objectMapper.writeValueAsString(metaRequest));
        } catch (JsonProcessingException e) {
            logger.warn("Empresa ID {}: Erro ao serializar metaRequest para debug: {}", company.getId(), e.getMessage());
        }
        try {
            String jsonPayloadForLog = objectMapper.writeValueAsString(metaRequest);
            logger.info("PAYLOAD FINAL ENVIADO PARA META: {}", jsonPayloadForLog); // Use INFO para garantir que apareça
        } catch (JsonProcessingException e) {
            logger.warn("Erro ao serializar metaRequest para debug", e);
        }

        return bspWebClient.post().uri(endpoint).body(BodyInserters.fromValue(metaRequest)).retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(responseNode -> {
                    saveSuccessMessageLog(responseNode, company, user, senderPhoneNumberId, recipientPhoneNumber,
                                          messageType, contentReference, scheduledMessageId);
                    billingService.recordMessagesSent(company, 1);
                    return Mono.empty();
                })
                .doOnError(WebClientResponseException.class, e -> {
                    saveFailedMessageLog(company, user, senderPhoneNumberId, recipientPhoneNumber,
                                         messageType, contentReference, e.getStatusCode().value(), e.getResponseBodyAsString());
                })
                .then();
    }

    private void saveSuccessMessageLog(JsonNode responseNode, Company company, User user, String sender, String recipientWaIdInput, String type, String contentReference, Long scheduledMessageId) {
        
        try {
            String wamid = responseNode.path("messages").get(0).path("id").asText(null);
            if (wamid == null) {
                logger.error("WAMID não encontrado na resposta de sucesso da Meta. Empresa ID {}: {}", company.getId(), responseNode);
                saveFailedMessageLog(company, user, sender, recipientWaIdInput, type, contentReference, 200, "Resposta OK, mas sem WAMID.");
                return;
            }

            WhatsAppMessageLog log = new WhatsAppMessageLog();
            log.setWamid(wamid);
            log.setCompany(company);
            log.setUser(user);
            log.setDirection(MessageDirection.OUTGOING);
            log.setSender(sender);
            log.setRecipient(recipientWaIdInput);
            log.setMessageType(type.toUpperCase());
            log.setContent(contentReference);
            log.setStatus("SENT");
            log.setScheduledMessageId(scheduledMessageId);
            messageLogRepository.save(log);

        } catch (Exception e) {
            logger.error("Falha CRÍTICA ao salvar log de mensagem enviada com sucesso para Empresa ID {}. Erro: {}", company.getId(), e.getMessage(), e);
        }
    }

    private void saveFailedMessageLog(Company company, User user, String sender, String recipient, String type, String contentReference, int httpStatus, String errorBody) {
        
        try {
            WhatsAppMessageLog log = new WhatsAppMessageLog();
            log.setCompany(company);
            log.setUser(user);
            log.setDirection(MessageDirection.OUTGOING);
            log.setSender(sender);
            log.setRecipient(recipient);
            log.setMessageType(type.toUpperCase());
            log.setContent(contentReference);
            log.setStatus("FAILED_API_ERROR");
            log.setMetadata(String.format("{\"httpStatus\": %d, \"errorBody\": \"%s\"}", httpStatus, escapeJson(errorBody)));
            log.setUpdatedAt(LocalDateTime.now());
            messageLogRepository.save(log);

        } catch (Exception e) {
            logger.error("Falha CRÍTICA ao salvar log de mensagem FALHA para Empresa ID {}: {}", company.getId(), e.getMessage(), e);
        }
    }

    private Mono<WhatsAppCloudApiRequest> buildTemplateMetaRequest(SendTemplateMessageRequest request, Company company, User user) {
        
        return Mono.fromCallable(() -> {
            List<TemplateComponentRequest> finalComponents;
            String recipientPhoneNumber;
            Contact targetContact = null; // Inicializa como nulo

            // Cenário 1: Envio por contactId (dinâmico)
            if (request.getContactId() != null) {
                targetContact = contactRepository.findByIdAndCompany(request.getContactId(), company)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Contato com ID " + request.getContactId() + " não encontrado para a empresa."
                    ));
                recipientPhoneNumber = targetContact.getPhoneNumber();
                
                // Gera os componentes com base nas regras de mapeamento e nos dados do contato
                finalComponents = parameterGenerator.generateComponents(
                    request.getComponents(), // As regras de mapeamento
                    targetContact,           // O contato para obter os dados
                    company,                 // O contexto da empresa
                    user                    // O usuário que está enviando
                );
            } 
            // Cenário 2: Envio direto por número de telefone 'to'
            else if (StringUtils.hasText(request.getTo())) {
                recipientPhoneNumber = request.getTo();
                // Opcional: tentar buscar o contato pelo número para usar seus dados
                targetContact = contactRepository.findByCompanyAndPhoneNumber(company, recipientPhoneNumber).orElse(null);

                // Gera os componentes. Se o contato foi encontrado, usa seus dados.
                // Se não, o parameterGenerator só poderá resolver 'company.*', 'user.*', 'fixedValue' e 'payloadValue'.
                finalComponents = request.getResolvedComponents();
            } else {
                throw new BusinessException("É necessário fornecer 'contactId' ou 'to' para enviar um template.");
            }

            // Constrói o payload para a API da Meta com os componentes já resolvidos
            WhatsAppTemplatePayload templatePayload = WhatsAppTemplatePayload.builder()
                    .name(request.getTemplateName())
                    .language(WhatsAppTemplatePayload.Language.builder().code(request.getLanguageCode()).build())
                    .components(mapToMetaComponents(finalComponents))
                    .build();

            return WhatsAppCloudApiRequest.builder()
                    .to(recipientPhoneNumber) // Usa o número de telefone resolvido
                    .type("template")
                    .template(templatePayload)
                    .build();
        });
    }

    private WhatsAppCloudApiRequest buildTextMetaRequest(SendTextMessageRequest request) {
        WhatsAppCloudApiRequest.TextPayload textPayload = WhatsAppCloudApiRequest.TextPayload.builder()
                .previewUrl(false).body(request.getMessage()).build();
        return WhatsAppCloudApiRequest.builder()
                .to(request.getTo()).type("text").text(textPayload).build();
    }

    private List<WhatsAppTemplatePayload.Component> mapToMetaComponents(List<TemplateComponentRequest> requestComponents) {
        if (requestComponents == null || requestComponents.isEmpty()) return Collections.emptyList();
        return requestComponents.stream()
            .map(compReq -> {
                if ("button".equalsIgnoreCase(compReq.getType()) && "flow".equalsIgnoreCase(compReq.getSub_type())) {
                    // Lógica especial para o botão de Flow
                    WhatsAppTemplatePayload.Component.ComponentBuilder buttonBuilder = WhatsAppTemplatePayload.Component.builder()
                            .type("button")
                            .subType("flow")
                            .index(compReq.getIndex());
                    
                    // O payload do botão de Flow está nos parâmetros com tipo 'action'
                    TemplateParameterRequest actionParam = compReq.getParameters().stream()
                        .filter(p -> "action".equalsIgnoreCase(p.getType()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("Botão do tipo Flow deve ter um parâmetro do tipo 'action'."));
                    
                    buttonBuilder.parameter(WhatsAppTemplatePayload.Parameter.builder()
                        .type("action")
                        .action(actionParam.getAction()) // Assumindo que você tem um campo 'action' em TemplateParameterRequest
                        .build());
                    return buttonBuilder.build();
                } else {
                    WhatsAppTemplatePayload.Component.ComponentBuilder componentBuilder = WhatsAppTemplatePayload.Component.builder()
                            .type(compReq.getType().toLowerCase())
                            .subType(compReq.getSub_type())
                            .index(compReq.getIndex());

                    if (compReq.getParameters() != null) {
                        compReq.getParameters().forEach(paramReq -> componentBuilder.parameter(mapToMetaParameter(paramReq)));
                    }
                    return componentBuilder.build();
                }
            }).collect(Collectors.toList());
    }

    private WhatsAppTemplatePayload.Parameter mapToMetaParameter(TemplateParameterRequest paramReq) {
         
        if (paramReq.getType() == null) {
            throw new IllegalArgumentException("Tipo do parâmetro não pode ser nulo.");
        }
        String typeLower = paramReq.getType().toLowerCase();
        WhatsAppTemplatePayload.Parameter.ParameterBuilder paramBuilder = WhatsAppTemplatePayload.Parameter.builder().type(typeLower);
    
        switch (typeLower) {
            case "text":
                if (paramReq.getText() == null) throw new IllegalArgumentException("Parâmetro 'text' não pode ser nulo para tipo 'text'.");
                paramBuilder.text(paramReq.getText());
                break;
            case "image":
            case "video":
            case "document":
                 if (paramReq.getMediaId() == null) throw new IllegalArgumentException("Parâmetro 'mediaId' não pode ser nulo para tipo '" + typeLower + "'.");
                 WhatsAppTemplatePayload.MediaObject.MediaObjectBuilder mediaBuilder = WhatsAppTemplatePayload.MediaObject.builder().id(paramReq.getMediaId());
                 if ("document".equals(typeLower) && paramReq.getFilename() != null && !paramReq.getFilename().isBlank()) {
                     mediaBuilder.filename(paramReq.getFilename());
                 }
                 WhatsAppTemplatePayload.MediaObject mediaObject = mediaBuilder.build();
                 if ("image".equals(typeLower)) 
                    paramBuilder.image(mediaObject);
                 else if ("video".equals(typeLower)) 
                    paramBuilder.video(mediaObject);
                 else 
                    paramBuilder.document(mediaObject);
                break;
            case "currency":
                 if (paramReq.getCurrency() == null) throw new IllegalArgumentException("Parâmetro 'currency' não pode ser nulo para tipo 'currency'.");
                 if (paramReq.getCurrency().getFallbackValue() == null || paramReq.getCurrency().getCode() == null || paramReq.getCurrency().getAmount1000() == null) {
                     throw new IllegalArgumentException("Campos 'fallbackValue', 'code', e 'amount1000' são obrigatórios para o tipo 'currency'.");
                 }
                 paramBuilder.currency(WhatsAppTemplatePayload.Currency.builder()
                         .fallbackValue(paramReq.getCurrency().getFallbackValue())
                         .code(paramReq.getCurrency().getCode().toUpperCase()) // Garante código da moeda em maiúsculas
                         .amount1000(paramReq.getCurrency().getAmount1000())
                         .build());
                break;
            case "date_time":
                  if (paramReq.getDateTime() == null) throw new IllegalArgumentException("Parâmetro 'dateTime' não pode ser nulo para tipo 'date_time'.");
                  if (paramReq.getDateTime().getFallbackValue() == null) {
                      throw new IllegalArgumentException("Campo 'fallbackValue' é obrigatório para o tipo 'date_time'.");
                  }
                  WhatsAppTemplatePayload.DateTime.DateTimeBuilder dateTimeBuilder = WhatsAppTemplatePayload.DateTime.builder()
                         .fallbackValue(paramReq.getDateTime().getFallbackValue());
    
                  paramBuilder.dateTime(dateTimeBuilder.build());
                break;
            case "payload": // Para botões quick_reply
                if (paramReq.getPayload() == null) throw new IllegalArgumentException("Parâmetro 'payload' não pode ser nulo para tipo 'payload'.");
                paramBuilder.payload(paramReq.getPayload());
                break;
            default:
                throw new IllegalArgumentException("Tipo de parâmetro desconhecido/não suportado no mapeamento: " + paramReq.getType());
        }
        return paramBuilder.build();
    }

    private Mono<WhatsAppCloudApiRequest> buildInteractiveFlowMetaRequest(SendInteractiveFlowMessageRequest request) {
        
        return Mono.fromCallable(() -> {
            String normalizedFlowName = com.br.alchieri.consulting.mensageria.util.StringUtils.normalizeMetaName(request.getFlowName());
            if (normalizedFlowName == null) {
                 throw new BusinessException("O nome do Flow não pode ser vazio.");
            }

            // --- LÓGICA DE TOKEN DE RASTREAMENTO ---
            String flowToken = request.getFlowToken();
            
            // Tenta encontrar o Flow no banco pelo Meta ID (que é passado como flowName/ID)
            Optional<Flow> optFlow = flowRepository.findByMetaFlowId(normalizedFlowName);
            
            if (optFlow.isPresent()) {
                // Se achou, força o token a ser o ID do banco (ex: "15")
                // Isso permite que o WebhookServiceImpl faça Long.parseLong(token) e ache o Flow
                flowToken = String.valueOf(optFlow.get().getId());
                logger.debug("Flow identificado no banco (ID {}). Token de rastreamento definido automaticamente.", flowToken);
            } else {
                // Se não achou, usa o que veio ou "unused"
                if (!StringUtils.hasText(flowToken)) {
                    flowToken = "unused";
                }
                logger.warn("Flow '{}' não encontrado no banco de dados. Usando token genérico: {}", normalizedFlowName, flowToken);
            }
            // ---------------------------------------

            Map<String, Object> flowParams = new HashMap<>();
            flowParams.put("flow_message_version", "3");
            flowParams.put("flow_name", normalizedFlowName);
            flowParams.put("flow_cta", request.getFlowCta());
            flowParams.put("flow_token", flowToken); // <<< Token injetado aqui
            
            if (StringUtils.hasText(request.getMode())) 
                flowParams.put("mode", request.getMode());
            if (StringUtils.hasText(request.getFlowAction())) 
                flowParams.put("flow_action", request.getFlowAction());
            if (request.getFlowActionPayload() != null) 
                flowParams.put("flow_action_payload", request.getFlowActionPayload());

            InteractivePayload.Action action = InteractivePayload.Action.builder()
                    .flowName("flow")
                    .flowParameters(flowParams)
                    .build();
            
            InteractivePayload interactivePayload = InteractivePayload.builder()
                    .type("flow")
                    .header(InteractivePayload.Header.builder().type("text").text(request.getHeaderText()).build())
                    .body(InteractivePayload.Body.builder().text(request.getBodyText()).build())
                    .footer(InteractivePayload.Footer.builder().text(request.getFooterText()).build())
                    .action(action)
                    .build();

            return WhatsAppCloudApiRequest.builder()
                    .to(request.getTo())
                    .type("interactive")
                    .interactive(interactivePayload)
                    .build();
        });
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new BusinessException("Nenhum usuário autenticado encontrado.");
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        if (userDetails instanceof User) {
            return (User) userDetails;
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado no sistema."));
    }

    private Company getCompanyFromUser(User user) {
        if (user == null) {
            throw new BusinessException("Usuário inválido.");
        }
        if (!user.getRoles().contains(Role.ROLE_BSP_ADMIN) && user.getCompany() == null) {
            throw new BusinessException("Usuário não está associado a uma empresa configurada.");
        }
        return user.getCompany(); // Pode ser nulo para BSP_ADMIN
    }

    private String getCompanyPhoneNumberId(Company company) {
        if (company == null || company.getMetaPrimaryPhoneNumberId() == null || company.getMetaPrimaryPhoneNumberId().isBlank()) {
            throw new BusinessException("Phone Number ID da Meta não configurado para a empresa.");
        }
        return company.getMetaPrimaryPhoneNumberId();
    }

    private WebClient getBspWebClient() {
        if (bspSystemUserAccessToken == null || bspSystemUserAccessToken.isBlank()) {
            throw new BusinessException("Token de System User do BSP não configurado.");
        }
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bspSystemUserAccessToken)
                .baseUrl(this.graphApiBaseUrl)
                .build();
    }

    private String escapeJson(String raw) {
        
        if (raw == null) return null;
        
        return raw.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                  .replace("\t", "\\t").replace("\b", "\\b").replace("\f", "\\f");
    }

    private WhatsAppCloudApiRequest buildSingleProductMetaRequest(SendProductMessageRequest request, Company company) {
        // Usa o catálogo da request ou o padrão da empresa
        String catalogId = request.getCatalogId() != null ? request.getCatalogId() : company.getMetaCatalogId();
        
        if (catalogId == null || catalogId.isBlank()) {
            throw new BusinessException("Catalog ID não configurado para a empresa. Configure no cadastro da empresa ou envie na requisição.");
        }

        // Action: Define qual produto mostrar
        InteractivePayload.Action action = InteractivePayload.Action.builder()
                .catalogId(catalogId)
                .productRetailerId(request.getProductRetailerId())
                .build();

        // Body e Footer (Opcionais)
        InteractivePayload.Body body = request.getBodyText() != null 
                ? InteractivePayload.Body.builder().text(request.getBodyText()).build() 
                : null;
                
        InteractivePayload.Footer footer = request.getFooterText() != null 
                ? InteractivePayload.Footer.builder().text(request.getFooterText()).build() 
                : null;

        // Monta o payload interativo
        InteractivePayload interactive = InteractivePayload.builder()
                .type("product")
                .body(body)
                .footer(footer)
                .action(action)
                .build();

        return WhatsAppCloudApiRequest.builder()
                .to(request.getTo())
                .type("interactive")
                .interactive(interactive)
                .build();
    }

    private WhatsAppCloudApiRequest buildMultiProductMetaRequest(SendMultiProductMessageRequest request, Company company) {
        String catalogId = request.getCatalogId() != null ? request.getCatalogId() : company.getMetaCatalogId();

        if (catalogId == null || catalogId.isBlank()) {
            throw new BusinessException("Catalog ID não configurado para a empresa.");
        }

        // Converte as seções do seu DTO para as seções da Meta
        List<InteractivePayload.Section> metaSections = request.getSections().stream()
                .map(sectionReq -> InteractivePayload.Section.builder()
                        .title(sectionReq.getTitle())
                        .productItems(sectionReq.getProductRetailerIds().stream()
                                .map(id -> InteractivePayload.ProductItem.builder().productRetailerId(id).build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        // Action: Contém o catálogo e as seções
        InteractivePayload.Action action = InteractivePayload.Action.builder()
                .catalogId(catalogId)
                .sections(metaSections)
                .build();

        // Monta o payload interativo (product_list exige Header)
        InteractivePayload interactive = InteractivePayload.builder()
                .type("product_list")
                .header(InteractivePayload.Header.builder()
                        .type("text")
                        .text(request.getHeaderText())
                        .build())
                .body(InteractivePayload.Body.builder().text(request.getBodyText()).build())
                .footer(request.getFooterText() != null 
                        ? InteractivePayload.Footer.builder().text(request.getFooterText()).build() 
                        : null)
                .action(action)
                .build();

        return WhatsAppCloudApiRequest.builder()
                .to(request.getTo())
                .type("interactive")
                .interactive(interactive)
                .build();
    }
}