package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CreateTemplateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.ClientTemplateDetailsResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.ClientTemplateResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplateSyncResponse;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppBusinessApiService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates", description = "Endpoints para gerenciamento de modelos de mensagem do WhatsApp.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TemplateController {

        private static final Logger logger = LoggerFactory.getLogger(TemplateController.class);

        private final WhatsAppBusinessApiService whatsAppBusinessApiService;
        private final SecurityUtils securityUtils;

        private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(60); // Timeout para operações de template

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Criar Modelo de Mensagem", description = "Submete um novo modelo de mensagem para aprovação pela Meta.")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Submissão de template aceita para processamento",
                                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requisição inválida ou erro da API Meta",
                                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado", content = @Content)
        })
        public Mono<ResponseEntity<ClientTemplateResponse>> createTemplate(
                @io.swagger.v3.oas.annotations.parameters.RequestBody(
                        description = "Definição completa do modelo de mensagem a ser criado.",
                        required = true,
                        content = @Content(schema = @Schema(implementation = CreateTemplateRequest.class))
                )
                @Valid @RequestBody CreateTemplateRequest request) {

                User currentUser = securityUtils.getAuthenticatedUser();

                logger.info("Usuário ID {}: Recebida requisição (bloqueante) para criar template: {}",
                        currentUser.getId(), request.getName());
                return whatsAppBusinessApiService.createTemplate(request, currentUser)
                        .map(savedTemplate -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ClientTemplateResponse.fromEntity(savedTemplate)));
        }

        @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Listar Templates",
                description = "Retorna uma lista paginada de templates. " +
                                "Usuários (USER/COMPANY_ADMIN) veem apenas os templates da sua própria empresa. " +
                                "Admins BSP podem usar o parâmetro 'companyId' para filtrar por uma empresa específica, ou omiti-lo para ver todos os templates de todas as empresas.")
        public ResponseEntity<Page<ClientTemplateResponse>> listTemplates(
                @Parameter(description = "ID da empresa para filtrar (apenas para admins BSP).")
                @RequestParam(required = false) Optional<Long> companyId,
                @ParameterObject Pageable pageable) {

                User currentUser = securityUtils.getAuthenticatedUser();
                
                // A lógica de filtragem e permissão agora está centralizada no serviço
                Page<ClientTemplate> templatePage = whatsAppBusinessApiService.listTemplatesForUser(currentUser, companyId, pageable);
                
                return ResponseEntity.ok(templatePage.map(ClientTemplateResponse::fromEntity));
        }

        @GetMapping(value = "/{templateName}/language/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Obter Detalhes Completos de um Template",
               description = "Retorna todas as informações de um template específico (nome e idioma), incluindo a estrutura JSON dos componentes, a partir do banco de dados local.")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detalhes do template recuperados.",
                        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ClientTemplateDetailsResponse.class))), // <<< NOVO DTO DE RESPOSTA
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Template não encontrado.", content = @Content)
        })
        public ResponseEntity<?> getTemplateDetails(
                        @Parameter(description = "Nome do template.", required = true) @PathVariable String templateName,
                        @Parameter(description = "Código do idioma do template (ex: pt_BR).", required = true) @PathVariable String language) {
                
                User currentUser = securityUtils.getAuthenticatedUser();
                logger.info("Usuário ID {}: Buscando detalhes do template '{}' ({})", currentUser.getId(), templateName, language);
                try {
                        // block() lança NoSuchElementException se o Mono for vazio,
                        // ou a exceção original se o Mono for de erro.
                        ClientTemplate template = whatsAppBusinessApiService.getTemplateDetails(templateName, language, currentUser)
                                .block(Duration.ofSeconds(10)); // Timeout

                        return ResponseEntity.ok(ClientTemplateDetailsResponse.fromEntity(template));

                } catch (ResourceNotFoundException e) {
                        logger.warn("Template '{}' ({}) não encontrado para o usuário ID {}.", templateName, language, currentUser.getId());
                        return ResponseEntity.notFound().build();
                } catch (AccessDeniedException e) {
                        logger.warn("Acesso negado ao buscar template '{}' ({}): {}", templateName, language, e.getMessage());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, e.getMessage(), null));
                } catch (Exception e) { // Captura timeout do block e outros erros
                        logger.error("Erro inesperado ao buscar detalhes do template '{}' ({}): {}", templateName, language, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ApiResponse(false, "Erro interno ao buscar detalhes do template.", null));
                }
        }

        @DeleteMapping(value = "/{templateName}/language/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Excluir Template",
                description = "Exclui um template da conta da Meta e do banco de dados local. Requer role de COMPANY_ADMIN ou BSP_ADMIN.")
        @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Template excluído com sucesso.",
                                        content = @Content(schema = @Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Falha ao excluir template (ex: erro da API Meta).",
                                        content = @Content(schema = @Schema(implementation = ApiResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Template não encontrado.",
                                        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
        })
        public ResponseEntity<ApiResponse> deleteTemplate( // <<< MUDANÇA: Retorno direto
                @Parameter(description = "Nome do template a ser excluído.", required = true) @PathVariable String templateName,
                @Parameter(description = "Código do idioma do template.", required = true) @PathVariable String language) {

                User currentUser = securityUtils.getAuthenticatedUser();
                logger.info("Usuário ID {}: Recebida requisição (bloqueante) para deletar template '{}' ({})",
                        currentUser.getId(), templateName, language);

                try {
                        // Chama o serviço reativo e bloqueia esperando o resultado (um ApiResponse)
                        ApiResponse response = whatsAppBusinessApiService.deleteTemplate(templateName, language, currentUser)
                                .block(BLOCK_TIMEOUT);

                        // Se o block() retornou a resposta do serviço, o envio foi bem-sucedido (ou a Meta retornou um erro estruturado)
                        // A lógica de status HTTP pode depender do 'success' no ApiResponse
                        HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
                        return ResponseEntity.status(status).body(response);

                } catch (ResourceNotFoundException e) {
                        logger.warn("Tentativa de deletar template não encontrado: '{}' ({}). Erro: {}", templateName, language, e.getMessage());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, e.getMessage(), null));
                } catch (AccessDeniedException e) {
                        logger.warn("Acesso negado ao tentar deletar template '{}' ({}): {}", templateName, language, e.getMessage());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, e.getMessage(), null));
                } catch (WebClientResponseException e) {
                        logger.error("Erro da API Meta (WebClientResponseException) ao deletar template '{}' ({}): Status={}, Body={}",
                                        templateName, language, e.getStatusCode(), e.getResponseBodyAsString());
                        ApiResponse errorResponse = new ApiResponse(false, "Falha ao deletar template (API Meta): " + e.getResponseBodyAsString(), null);
                        return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
                } catch (RuntimeException e) { // Captura timeout do block() e outras exceções
                        if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                                logger.error("Timeout ao deletar template '{}' ({}): {}", templateName, language, e.getMessage());
                                ApiResponse errorResponse = new ApiResponse(false, "Timeout ao processar a solicitação de exclusão.", null);
                                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
                        }
                        logger.error("Erro inesperado (bloqueante) ao deletar template '{}' ({}): {}", templateName, language, e.getMessage(), e);
                        ApiResponse errorResponse = new ApiResponse(false, "Erro interno ao processar a exclusão.", null);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
        }

        @PostMapping(value = "/sync-from-meta", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Sincronizar Templates da Meta",
                description = "Busca todos os templates da conta WhatsApp Business associada e os importa/atualiza no banco de dados local. Usuários normais só podem sincronizar sua própria empresa. Admins podem precisar de um endpoint separado para sincronizar para outros.")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sincronização concluída.",
                        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = TemplateSyncResponse.class))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno durante a sincronização.",
                        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
        })
        @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
        public ResponseEntity<?> syncTemplates() {
                User currentUser = securityUtils.getAuthenticatedUser();
                logger.info("Usuário ID {}: Iniciou a sincronização de templates da Meta para sua empresa.", currentUser.getId());

                try {
                        // Chama o serviço reativo e bloqueia esperando o resultado
                        TemplateSyncResponse response = whatsAppBusinessApiService.syncTemplatesFromMeta(currentUser)
                                .block(BLOCK_TIMEOUT); // Timeout generoso para a sincronização

                        if (response == null) {
                                logger.warn("O serviço de sincronização não retornou um resultado para o usuário ID {}.", currentUser.getId());
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(new ApiResponse(false, "O serviço de sincronização não retornou um resultado.", null));
                        }

                        return ResponseEntity.ok(response);

                } catch (WebClientResponseException e) {
                        // Se a chamada para listar templates (dentro do serviço) falhar
                        logger.error("Erro da API Meta durante a sincronização para o usuário ID {}: {}", currentUser.getId(), e.getResponseBodyAsString(), e);
                        ApiResponse errorResponse = new ApiResponse(false, "Falha ao sincronizar templates (API Meta): " + e.getResponseBodyAsString(), null);
                        return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
                } catch (AccessDeniedException e) {
                        logger.warn("Acesso negado ao sincronizar templates para o usuário ID {}: {}", currentUser.getId(), e.getMessage());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse(false, e.getMessage(), null));
                } catch (Exception e) {
                        logger.error("Erro inesperado durante a sincronização de templates para o usuário ID {}: {}", currentUser.getId(), e.getMessage(), e);
                        ApiResponse errorResponse = new ApiResponse(false, "Erro interno durante a sincronização: " + e.getMessage(), null);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
        }

        @GetMapping(value = "/{templateName}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Buscar Templates por Nome",
                description = "Retorna todas as versões de idioma de um template específico pelo seu nome, para a empresa do usuário autenticado.")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Templates encontrados.",
                        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        array = @ArraySchema(schema = @Schema(implementation = ClientTemplateResponse.class)))),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nenhum template encontrado com este nome para sua empresa.", content = @Content)
        })
        public ResponseEntity<List<ClientTemplateResponse>> getTemplatesByName(
                @Parameter(description = "Nome exato do template a ser buscado.", required = true)
                @PathVariable String templateName
        ) {
                User currentUser = securityUtils.getAuthenticatedUser();
                logger.info("Usuário ID {}: Buscando templates com nome '{}'", currentUser.getId(), templateName);

                List<ClientTemplate> templates = whatsAppBusinessApiService.findTemplatesByNameForUser(templateName, currentUser);

                if (templates.isEmpty()) {
                return ResponseEntity.notFound().build();
                }

                List<ClientTemplateResponse> response = templates.stream()
                        .map(ClientTemplateResponse::fromEntity)
                        .collect(Collectors.toList());
                
                return ResponseEntity.ok(response);
        }
}
