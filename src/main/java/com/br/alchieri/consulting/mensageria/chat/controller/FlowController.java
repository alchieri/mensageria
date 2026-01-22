package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.Duration;
import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowJsonUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.FlowUpdateRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowMetricResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.FlowResponse;
import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowMetricName;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MetricGranularity;
import com.br.alchieri.consulting.mensageria.chat.service.FlowService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/flows")
@Tag(name = "Flows", description = "Endpoints para gerenciamento do ciclo de vida de WhatsApp Flows.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    private User getAuthenticatedUserAndCompany() {
        User user = securityUtils.getAuthenticatedUser();
        if (user.getCompany() == null) {
            throw new BusinessException("Usuário não está associado a uma empresa para gerenciar Flows.");
        }
        return user;
    }

    @PostMapping
    @Operation(summary = "Criar um Novo Flow",
               description = "Cria um novo Flow na plataforma da Meta. Por padrão, ele é criado em status DRAFT. Use o parâmetro 'publish' para tentar publicá-lo imediatamente.")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<?> createFlow(
            @Valid @RequestBody FlowRequest request,
            @Parameter(description = "Se 'true', tenta publicar o Flow imediatamente após a criação.")
            @RequestParam(defaultValue = "false") boolean publish) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow createdFlow = flowService.createFlow(request, currentUser.getCompany(), publish).block(BLOCK_TIMEOUT);
            return ResponseEntity.status(HttpStatus.CREATED).body(FlowResponse.fromEntity(createdFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar Flow: " + e.getMessage(), e);
        }
    }
    
    @PatchMapping("/{flowId}/metadata")
    @Operation(summary = "Atualizar Metadados de um Flow",
               description = "Atualiza o nome amigável, as categorias ou a URI do endpoint de um Flow existente.")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<?> updateFlowMetadata(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowUpdateRequest request) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow updatedFlow = flowService.updateFlowMetadata(flowId, request, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(FlowResponse.fromEntity(updatedFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atualizar metadados do Flow: " + e.getMessage(), e);
        }
    }

    @PutMapping("/{flowId}/json")
    @Operation(summary = "Atualizar o JSON de um Flow",
               description = "Substitui a definição JSON de um Flow existente. Após esta operação, o Flow voltará ao status DRAFT na Meta e precisará ser publicado novamente.")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<?> updateFlowJson(
            @PathVariable Long flowId,
            @Valid @RequestBody FlowJsonUpdateRequest request) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow updatedFlow = flowService.updateFlowJson(flowId, request, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(FlowResponse.fromEntity(updatedFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atualizar o JSON do Flow: " + e.getMessage(), e);
        }
    }
    
    @PostMapping("/{flowId}/publish")
    @Operation(summary = "Publicar um Flow",
               description = "Publica a versão mais recente de um Flow que está em status DRAFT na Meta, tornando-o ativo para uso.")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<FlowResponse> publishFlow(@PathVariable Long flowId) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow publishedFlow = flowService.publishFlow(flowId, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(FlowResponse.fromEntity(publishedFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao publicar Flow: " + e.getMessage(), e);
        }
    }
    
    @GetMapping
    @Operation(summary = "Listar Flows da Empresa")
    public ResponseEntity<Page<FlowResponse>> listFlows(@ParameterObject Pageable pageable) {
        User currentUser = getAuthenticatedUserAndCompany();
        Page<Flow> flowPage = flowService.listFlowsByCompany(currentUser.getCompany(), pageable);
        return ResponseEntity.ok(flowPage.map(flow -> FlowResponse.fromEntity(flow, objectMapper)));
    }

    @GetMapping("/{flowId}")
    @Operation(summary = "Obter Detalhes de um Flow")
    public ResponseEntity<FlowResponse> getFlow(@PathVariable Long flowId) {
        User currentUser = getAuthenticatedUserAndCompany();
        return flowService.getFlowByIdAndCompany(flowId, currentUser.getCompany())
                .map(flow -> ResponseEntity.ok(FlowResponse.fromEntity(flow, objectMapper)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{flowId}")
    @Operation(summary = "Excluir um Flow")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<ApiResponse> deleteFlow(@PathVariable Long flowId) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            ApiResponse response = flowService.deleteFlow(flowId, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar Flow: " + e.getMessage(), e);
        }
    }

    @PostMapping("/{flowId}/deprecate")
    @Operation(summary = "Desativar (Deprecate) um Flow")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<FlowResponse> deprecateFlow(@PathVariable Long flowId) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow deprecatedFlow = flowService.deprecateFlow(flowId, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(FlowResponse.fromEntity(deprecatedFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao desativar Flow: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{flowId}/sync-status")
    @Operation(summary = "Sincronizar Status de um Flow com a Meta")
    public ResponseEntity<FlowResponse> syncFlowStatus(@PathVariable Long flowId) {
        User currentUser = getAuthenticatedUserAndCompany();
        try {
            Flow syncedFlow = flowService.fetchAndSyncFlowStatus(flowId, currentUser.getCompany()).block(BLOCK_TIMEOUT);
            return ResponseEntity.ok(FlowResponse.fromEntity(syncedFlow, objectMapper));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao sincronizar status do Flow: " + e.getMessage(), e);
        }
    }

    @GetMapping("/{flowId}/metrics")
    @Operation(summary = "Obter Métricas de um Flow",
               description = "Busca métricas de performance de um Flow específico (ex: contagem de erros, latência) da API da Meta para um determinado período.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Métricas recuperadas com sucesso.",
            content = @Content(schema = @Schema(implementation = FlowMetricResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou não há dados suficientes.",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Flow não encontrado.", content = @Content)
    })
    public Mono<ResponseEntity<FlowMetricResponse>> getFlowMetrics(
            @Parameter(description = "ID do Flow (do seu sistema) a ser consultado.", required = true)
            @PathVariable Long flowId,
            
            @Parameter(description = "Nome da métrica a ser buscada.", required = true)
            @RequestParam FlowMetricName metricName,

            @Parameter(description = "Granularidade do tempo para a métrica.", required = true)
            @RequestParam MetricGranularity granularity,

            @Parameter(description = "Data de início da consulta (formato YYYY-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,

            @Parameter(description = "Data de fim da consulta (formato YYYY-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        // O método no serviço já trata ResourceNotFound e AccessDenied
        return flowService.getFlowMetrics(flowId, currentUser.getCompany(), metricName, granularity, since, until)
                .map(ResponseEntity::ok);
    }
}
