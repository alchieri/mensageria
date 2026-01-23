package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.request.BulkMessageTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.OutgoingMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendInteractiveFlowMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMediaMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendMultiProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendProductMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTemplateMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.SendTextMessageRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.BulkMessageResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.MessageStatusResponse;
import com.br.alchieri.consulting.mensageria.chat.service.BulkMessageService;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Endpoints para envio de mensagens WhatsApp via Cloud API.")
//@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    private final BulkMessageService bulkMessageService;
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(10);

    private final SecurityUtils securityUtils;

    @Value("${sqs.queue.outgoing}")
    private String outgoingQueueName;

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enfileirar Mensagem de Texto Simples",
            description = "Enfileira uma solicitação para enviar uma mensagem de texto simples. O envio real é assíncrono. Requer janela de 24h ativa no momento do envio pelo consumidor."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Solicitação de envio recebida e enfileirada.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requisição inválida (dados faltando).",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))), // Ou ErrorResponse
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno ao enfileirar.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))) // Ou ErrorResponse
    })
    public ResponseEntity<ApiResponse> sendTextMessage(
            @Parameter(description = "Dados da mensagem de texto a ser enviada.", required = true, content = @Content(schema = @Schema(implementation = SendTextMessageRequest.class)))
            @Valid @RequestBody SendTextMessageRequest request) {

        User currentUser = securityUtils.getAuthenticatedUser();
        request.setTo(normalizePhoneNumber(request.getTo()));
        logger.info("Usuário ID {}: Recebida requisição para ENFILEIRAR (SQS) mensagem de texto para: {}", currentUser.getId(), request.getTo());

        OutgoingMessageRequest queuePayload = OutgoingMessageRequest.builder()
                .messageType("TEXT")
                .userId(currentUser.getId())
                .textRequest(request)
                .originalRequestId(MDC.get("traceId"))
                .build();

        try {
            String jsonPayload = objectMapper.writeValueAsString(queuePayload);
            String messageGroupId = "company-" + (currentUser.getCompany() != null ? currentUser.getCompany().getId() : "no-company");
            
            sqsTemplate.send(to -> to.queue(outgoingQueueName)
                                      .payload(jsonPayload)
                                      .header("message-group-id", messageGroupId));

            logger.info("Mensagem de texto para {} (solicitada pelo Usuário ID {}) enfileirada (SQS) com sucesso.", request.getTo(), currentUser.getId());
            return ResponseEntity.accepted().body(new ApiResponse(true, "Solicitação de envio de texto recebida e enfileirada.", null));
        } catch (Exception e) {
            logger.error("Falha ao enfileirar (SQS) mensagem de texto para {} (solicitada pelo Usuário ID {}): {}",
                         request.getTo(), currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(new ApiResponse(false, "Erro ao enfileirar a solicitação de envio.", null));
        }
    }

    @PostMapping(value = "/template", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Enfileirar Mensagem de Modelo (Template)",
            description = "Enfileira uma solicitação para enviar uma mensagem baseada em um modelo pré-aprovado. O envio real é assíncrono."
    )
     @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Solicitação de envio recebida e enfileirada.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requisição inválida.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno ao enfileirar.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse> sendTemplateMessage(
            @Parameter(description = "Dados da mensagem de modelo a ser enviada.", required = true, content = @Content(schema = @Schema(implementation = SendTemplateMessageRequest.class)))
            @Valid @RequestBody SendTemplateMessageRequest request
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        request.setTo(normalizePhoneNumber(request.getTo()));
        logger.info("Usuário ID {}: Recebida requisição para ENFILEIRAR (SQS) template '{}' para: {}",
                currentUser.getId(), request.getTemplateName(), request.getTo());

        OutgoingMessageRequest queuePayload = OutgoingMessageRequest.builder()
                .messageType("TEMPLATE")
                .userId(currentUser.getId()) // <<< PASSA O ID DO USUÁRIO
                .templateRequest(request)
                .originalRequestId(MDC.get("traceId"))
                .build();

        try {
            String jsonPayload = objectMapper.writeValueAsString(queuePayload);
            String messageGroupId = "company-" + (currentUser.getCompany() != null ? currentUser.getCompany().getId() : "no-company");

            sqsTemplate.send(to -> to.queue(outgoingQueueName)
                                      .payload(jsonPayload)
                                      .header("message-group-id", messageGroupId));

            logger.info("Mensagem de template '{}' para {} (solicitada pelo Usuário ID {}) enfileirada (SQS) com sucesso.",
                        request.getTemplateName(), request.getTo(), currentUser.getId());
            return ResponseEntity.accepted().body(new ApiResponse(true, "Solicitação de envio de template recebida e enfileirada.", null));
        } catch (Exception e) {
            logger.error("Falha ao enfileirar (SQS) mensagem de template '{}' para {} (solicitada pelo Usuário ID {}): {}",
                         request.getTemplateName(), request.getTo(), currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(new ApiResponse(false, "Erro ao enfileirar a solicitação de envio.", null));
        }
    }

    @PostMapping(value = "/interactive/flow", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enfileirar Envio de Mensagem Interativa de Flow",
               description = "Inicia um Flow dentro de uma conversa existente (requer janela de 24h ativa). O envio é assíncrono.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Solicitação de envio recebida e enfileirada.", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos.", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content)
    })
    public ResponseEntity<ApiResponse> sendInteractiveFlow(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Configuração da mensagem interativa de Flow.", required = true)
            @Valid @RequestBody SendInteractiveFlowMessageRequest request
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        request.setTo(normalizePhoneNumber(request.getTo()));
        logger.info("Usuário ID {}: Enfileirando Flow Interativo '{}' para {}",
                currentUser.getId(), request.getFlowName(), request.getTo());
                
        OutgoingMessageRequest queuePayload = OutgoingMessageRequest.builder()
                .messageType("INTERACTIVE_FLOW") // Novo tipo para o consumidor
                .userId(currentUser.getId())
                .interactiveFlowRequest(request) // Novo campo no DTO da fila
                .originalRequestId(MDC.get("traceId"))
                .build();
        
        try {
            String jsonPayload = objectMapper.writeValueAsString(queuePayload);
            String messageGroupId = "company-" + (currentUser.getCompany() != null ? currentUser.getCompany().getId() : "no-company");

            sqsTemplate.send(to -> to.queue(outgoingQueueName)
                                      .payload(jsonPayload)
                                      .header("message-group-id", messageGroupId));

            logger.info("Mensagem de flow '{}' para {} (solicitada pelo Usuário ID {}) enfileirada (SQS) com sucesso.",
                        request.getFlowName(), request.getTo(), currentUser.getId());
            return ResponseEntity.accepted().body(new ApiResponse(true, "Solicitação de envio de flow recebida e enfileirada.", null));
        } catch (Exception e) {
            logger.error("Falha ao enfileirar (SQS) mensagem de flow '{}' para {} (solicitada pelo Usuário ID {}): {}",
                         request.getFlowName(), request.getTo(), currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(new ApiResponse(false, "Erro ao enfileirar a solicitação de envio.", null));
        }
    }

    @PostMapping("/media")
    @Operation(summary = "Enviar Mensagem de Mídia", description = "Envia imagem, vídeo, documento ou áudio usando um ID de mídia.")
    public ResponseEntity<ApiResponse> sendMediaMessage(
            @Valid @RequestBody SendMediaMessageRequest request) {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        
        // Normaliza telefone
        request.setTo(normalizePhoneNumber(request.getTo()));
        
        // Executa envio
        whatsAppCloudApiService.sendMediaMessage(request, currentUser).block();

        return ResponseEntity.ok(new ApiResponse(true, "Mensagem de mídia enviada/enfileirada.", null));
    }

    // O endpoint GET para status permanece, mas agora ele consulta o banco de dados
    // e não depende mais diretamente do resultado do envio (que agora é assíncrono)
    @GetMapping(value = "/{wamid}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar Status da Mensagem", description = "Retorna o último status conhecido de uma mensagem (enviada anteriormente), identificado pelo WAMID.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status da mensagem recuperado com sucesso.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MessageStatusResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido (mensagem de outro cliente).", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mensagem não encontrada.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno.", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<?> getMessageStatus(
            @Parameter(description = "WAMID (WhatsApp Message ID) da mensagem a ser consultada.", example = "wamid.HBgM...", required = true)
            @PathVariable String wamid
    ) {
        
        logger.info("Recebida requisição para consultar status do WAMID: {}", wamid);

        try {
            // Chama o serviço reativo e bloqueia para obter o resultado.
            // O serviço retorna Mono<MessageStatusResponse> ou Mono.empty() ou Mono.error().
            MessageStatusResponse statusResponse = whatsAppCloudApiService.getMessageStatusByWamid(wamid)
                    .block(BLOCK_TIMEOUT); // Espera o resultado ou um timeout

            if (statusResponse != null) {
                // Se o block() retornou um objeto, foi sucesso.
                return ResponseEntity.ok(statusResponse);
            } else {
                // Se o block() retornou null (o que acontece se o Mono for vazio - switchIfEmpty), é Not Found.
                logger.warn("Nenhum status de mensagem encontrado para WAMID: {}", wamid);
                return ResponseEntity.notFound().build();
            }
        } catch (AccessDeniedException e) {
            // Captura a exceção de segurança lançada pelo serviço
            logger.warn("Acesso negado ao buscar status do WAMID {}: {}", wamid, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                 .body(new ApiResponse(false, e.getMessage(), null));
        } catch (RuntimeException e) {
            // Captura timeout do block() ou outras exceções de runtime
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                 logger.error("Timeout ao buscar status para WAMID {}: {}", wamid, e.getMessage());
                 return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                                      .body(new ApiResponse(false, "Timeout ao processar a solicitação.", null));
            }
            logger.error("Erro inesperado ao buscar status para WAMID {}: {}", wamid, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse(false, "Erro interno ao buscar status da mensagem.", null));
        }
    }

    @GetMapping(value = "/log/{logId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar Mensagem por ID do Log",
               description = "Retorna o status e os detalhes de qualquer mensagem registrada no sistema (incluindo falhas) usando o ID interno do log.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detalhes da mensagem recuperados.", content = @Content(schema = @Schema(implementation = MessageStatusResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Log de mensagem não encontrado.", content = @Content)
    })
    public ResponseEntity<?> getMessageStatusByLogId(
            @Parameter(description = "ID do log da mensagem no banco de dados.", required = true)
            @PathVariable Long logId
    ) {
        logger.info("Recebida requisição para consultar status pelo ID do Log: {}", logId);

        try {
            // Chama o serviço reativo e bloqueia para obter o resultado.
            // O serviço retorna Mono<MessageStatusResponse> ou Mono.empty() ou Mono.error().
            MessageStatusResponse statusResponse = whatsAppCloudApiService.getMessageStatusByLogId(logId)
                    .block(BLOCK_TIMEOUT); // Espera o resultado ou um timeout

            if (statusResponse != null) {
                // Se o block() retornou um objeto, foi sucesso.
                return ResponseEntity.ok(statusResponse);
            } else {
                // Se o block() retornou null (o que acontece se o Mono for vazio - switchIfEmpty), é Not Found.
                logger.warn("Nenhum status de mensagem encontrado para ID do Log: {}", logId);
                return ResponseEntity.notFound().build();
            }
        } catch (AccessDeniedException e) {
            // Captura a exceção de segurança lançada pelo serviço
            logger.warn("Acesso negado ao buscar status do ID do Log {}: {}", logId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                 .body(new ApiResponse(false, e.getMessage(), null));
        } catch (RuntimeException e) {
            // Captura timeout do block() ou outras exceções de runtime
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                 logger.error("Timeout ao buscar status para ID do Log {}: {}", logId, e.getMessage());
                 return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                                      .body(new ApiResponse(false, "Timeout ao processar a solicitação.", null));
            }
            logger.error("Erro inesperado ao buscar status para ID do Log {}: {}", logId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse(false, "Erro interno ao buscar status da mensagem.", null));
        }
    }

    @PostMapping(value = "/bulk/template", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enfileirar Envio em Massa de Template",
               description = "Inicia um job de envio em massa de uma mensagem de template para um grupo de contatos, segmentado por tags ou por uma lista de números. O envio é assíncrono.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Job de envio em massa aceito e enfileirado.",
                    content = @Content(schema = @Schema(implementation = BulkMessageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requisição inválida (ex: limite excedido, nenhum contato encontrado).",
                    content = @Content(schema = @Schema(implementation = BulkMessageResponse.class))), // Ou ApiResponse/ErrorResponse
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content)
    })
    public ResponseEntity<BulkMessageResponse> sendBulkTemplateMessage(
            @Parameter(description = "Configuração do envio em massa.", required = true,
                                content = @Content(schema = @Schema(implementation = BulkMessageTemplateRequest.class)))
            @Valid @RequestBody BulkMessageTemplateRequest request
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        if (currentCompany == null) {
            throw new BusinessException("Apenas usuários associados a uma empresa podem iniciar envios em massa.");
        }

        logger.info("Empresa ID {}: Recebida requisição de envio em massa para o template '{}'",
                currentCompany.getId(), request.getTemplateName());

        BulkMessageResponse response = bulkMessageService.startBulkTemplateJob(request, currentCompany, currentUser);

        // Retorna 202 Accepted se o job foi enfileirado, ou 400 Bad Request se foi bloqueado por algum motivo
        HttpStatus status = "QUEUED".equals(response.getStatus()) ? HttpStatus.ACCEPTED : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/interactive/product")
    @Operation(summary = "Enviar Mensagem de Produto Único", description = "Envia um único produto do catálogo.")
    public ResponseEntity<ApiResponse> sendProduct(
            @Valid @RequestBody SendProductMessageRequest request) {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        // Normaliza telefone (reaproveitando sua lógica)
        request.setTo(normalizePhoneNumber(request.getTo()));
        
        whatsAppCloudApiService.sendProductMessage(request, currentUser).block();
        
        return ResponseEntity.ok(new ApiResponse(true, "Mensagem de produto enviada/enfileirada com sucesso.", null));
    }

    @PostMapping("/interactive/product-list")
    @Operation(summary = "Enviar Lista de Produtos", description = "Envia uma lista de até 30 produtos organizados em seções.")
    public ResponseEntity<ApiResponse> sendMultiProduct(
            @Valid @RequestBody SendMultiProductMessageRequest request) {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        request.setTo(normalizePhoneNumber(request.getTo()));
        
        whatsAppCloudApiService.sendMultiProductMessage(request, currentUser).block();
        
        return ResponseEntity.ok(new ApiResponse(true, "Mensagem multiproduto enviada/enfileirada com sucesso.", null));
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        String cleaned = phoneNumber.replaceAll("\\D", ""); // Remove não numéricos
        
        // Lógica do 9º dígito: Se for 55 + DDD + 8 digitos -> Insere o 9
        if (cleaned.startsWith("55") && cleaned.length() == 12) {
            String normalized = cleaned.substring(0, 4) + "9" + cleaned.substring(4);
            // logger.debug("Normalizando telefone no envio: {} -> {}", cleaned, normalized);
            return normalized;
        }
        return cleaned;
    }
}
