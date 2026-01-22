package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MediaUploadResponse;
import com.br.alchieri.consulting.mensageria.chat.model.MediaUpload;
import com.br.alchieri.consulting.mensageria.chat.service.MediaService;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppCloudApiService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Endpoints para upload e listagem de mídias para uso com WhatsApp.")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);
    private final WhatsAppCloudApiService whatsAppCloudApiService;
    private final MediaService mediaService;
    private final SecurityUtils securityUtils;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload de Mídia", description = "Faz upload de um arquivo de mídia (imagem, vídeo, documento, áudio) para a API do WhatsApp e retorna um ID de mídia.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload bem-sucedido, retorna o ID da mídia.",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requisição inválida (ex: arquivo vazio, erro Meta)",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno do servidor",
                         content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse> uploadMedia(
            @Parameter(description = "Arquivo de mídia a ser carregado.", required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Produto de mensageria (geralmente 'whatsapp').", example = "whatsapp", schema = @Schema(type = "string", defaultValue = "whatsapp"))
            @RequestParam(value = "messaging_product", defaultValue = "whatsapp") String product) {

        // 1. Obter o usuário autenticado
        User currentUser = securityUtils.getAuthenticatedUser();
        logger.info("Usuário ID {}: Recebida requisição para upload de mídia: {}", currentUser.getId(), file.getOriginalFilename());

        if (file.isEmpty()) {
            logger.warn("Usuário ID {}: Tentativa de upload de arquivo vazio.", currentUser.getId());
            return ResponseEntity.badRequest()
                      .body(new ApiResponse(false, "Arquivo não pode estar vazio.", null));
        }

        try {
            // 2. Chamar o método de serviço, passando o usuário
            String mediaId = whatsAppCloudApiService.uploadMedia(file, product, currentUser).block(BLOCK_TIMEOUT);

            if (mediaId == null) {
                throw new RuntimeException("ID da mídia não retornado pelo serviço de upload.");
            }
            return ResponseEntity.ok(new ApiResponse(true, "Upload de mídia bem-sucedido.", Map.of("mediaId", mediaId)));

        } catch (WebClientResponseException e) {
            logger.error("Erro da API Meta (WebClientResponseException) no upload para o Usuário ID {}: Status={}, Body={}",
                         currentUser.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            ApiResponse errorResponse = new ApiResponse(false, "Falha no upload (API Meta): " + e.getResponseBodyAsString(), null);
            return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
        } catch (BusinessException | IllegalArgumentException e) { // Captura erros de negócio e validação
             logger.warn("Falha no upload para o Usuário ID {}: {}", currentUser.getId(), e.getMessage());
             ApiResponse errorResponse = new ApiResponse(false, "Falha no upload: " + e.getMessage(), null);
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (RuntimeException e) { // Captura timeout do block()
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                 logger.error("Timeout durante o upload da mídia para o Usuário ID {}: {}", currentUser.getId(), e.getMessage());
                 ApiResponse errorResponse = new ApiResponse(false, "Timeout durante o upload da mídia.", null);
                 return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
            }
             logger.error("Erro inesperado (bloqueante) durante o upload da mídia para o Usuário ID {}: {}", currentUser.getId(), e.getMessage(), e);
             ApiResponse errorResponse = new ApiResponse(false, "Erro interno no servidor durante o upload: " + e.getMessage(), null);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Mídias Carregadas",
               description = "Retorna uma lista paginada de mídias que foram carregadas. " +
                             "Usuários normais veem apenas as mídias da sua própria empresa. " +
                             "Admins BSP podem filtrar por 'companyId' ou ver todas.")
    public ResponseEntity<Page<MediaUploadResponse>> listMedia(
            @Parameter(description = "ID da empresa para filtrar (apenas para admins BSP).")
            @RequestParam(required = false) Optional<Long> companyId,
            @ParameterObject Pageable pageable
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Page<MediaUpload> mediaPage = mediaService.listMediaForUser(currentUser, companyId, pageable);
        return ResponseEntity.ok(mediaPage.map(MediaUploadResponse::fromEntity));
    }

    @GetMapping(value = "/{mediaId}/download-url", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obter URL de Download para Mídia",
               description = "Gera uma URL temporária e segura (pré-assinada) para baixar um arquivo de mídia armazenado.")
    public ResponseEntity<ApiResponse> getDownloadUrl(
            @Parameter(description = "ID da mídia (do seu banco) para a qual gerar a URL.", required = true)
            @PathVariable Long mediaId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        String url = mediaService.getPresignedUrlForDownload(mediaId, currentUser);
        
        // Retorna a URL em um objeto JSON
        return ResponseEntity.ok(new ApiResponse(true, "URL de download gerada com sucesso.", Map.of("url", url)));
    }
}
