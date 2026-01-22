package com.br.alchieri.consulting.mensageria.chat.controller;

import java.util.Optional;

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

import com.br.alchieri.consulting.mensageria.chat.dto.request.ScheduleCampaignRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.response.ScheduledCampaignResponse;
import com.br.alchieri.consulting.mensageria.chat.dto.response.ScheduledCampaignSummaryResponse;
import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.chat.service.CampaignService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/campaigns")
@Tag(name = "Campaigns", description = "Endpoints para agendamento e gerenciamento de campanhas de mensagens.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CampaignController {

    private static final Logger logger = LoggerFactory.getLogger(CampaignController.class);
    private final CampaignService campaignService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/schedule", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Agendar Nova Campanha",
               description = "Cria e agenda uma campanha para envio de mensagens em massa para um público-alvo (definido por tags ou IDs de contato).")
    public ResponseEntity<ScheduledCampaignResponse> scheduleCampaign(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Detalhes da campanha a ser agendada.", required = true,
                                content = @Content(schema = @Schema(implementation = ScheduleCampaignRequest.class)))
            @Valid @RequestBody ScheduleCampaignRequest request
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        logger.info("Usuário ID {}: Recebida requisição para agendar campanha '{}'.", currentUser.getId(), request.getCampaignName());
        ScheduledCampaign campaign = campaignService.scheduleNewCampaign(request, currentUser);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ScheduledCampaignResponse.fromEntity(campaign));
    }

    @GetMapping
    @Operation(summary = "Listar Campanhas",
               description = "Retorna uma lista paginada de campanhas. " +
                             "Usuários normais veem apenas as campanhas da sua própria empresa. " +
                             "Admins BSP podem filtrar por 'companyId' para ver as de uma empresa específica, ou ver todas se o filtro não for fornecido.")
    public ResponseEntity<Page<ScheduledCampaignSummaryResponse>> listCampaigns(
            @Parameter(description = "ID da empresa para filtrar (apenas para admins BSP).")
            @RequestParam(required = false) Optional<Long> companyId, // Parâmetro opcional
            @ParameterObject Pageable pageable) {

        User currentUser = securityUtils.getAuthenticatedUser();
        Page<ScheduledCampaign> campaignPage;

        // Lógica baseada na role
        if (currentUser.getRoles().contains(Role.ROLE_BSP_ADMIN)) {
            if (companyId.isPresent()) {
                // Admin quer filtrar por uma empresa específica
                campaignPage = campaignService.listCampaignsByCompanyId(companyId.get(), pageable);
            } else {
                // Admin quer ver todas as campanhas de todas as empresas
                campaignPage = campaignService.listAllCampaigns(pageable);
            }
        } else { // Usuário é COMPANY_ADMIN ou USER
            // Garante que o usuário está associado a uma empresa
            Company currentCompany = currentUser.getCompany();
            if (currentCompany == null) {
                throw new BusinessException("Usuário não está associado a uma empresa para listar campanhas.");
            }
            // Impede que um usuário normal tente filtrar por outra empresa
            if (companyId.isPresent() && !companyId.get().equals(currentCompany.getId())) {
                throw new AccessDeniedException("Você não tem permissão para visualizar campanhas de outra empresa.");
            }
            campaignPage = campaignService.listCampaignsByCompany(currentCompany, pageable);
        }

        return ResponseEntity.ok(campaignPage.map(ScheduledCampaignSummaryResponse::fromEntity));
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Obter Detalhes da Campanha")
    public ResponseEntity<ScheduledCampaignResponse> getCampaignDetails(
            @Parameter(description = "ID da campanha.") @PathVariable Long campaignId) {
        User currentUser = securityUtils.getAuthenticatedUser();
        return campaignService.getCampaignDetails(campaignId, currentUser)
                .map(campaign -> ResponseEntity.ok(ScheduledCampaignResponse.fromEntity(campaign)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{campaignId}/pause")
    @Operation(summary = "Pausar Campanha")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')") // Apenas admins podem pausar
    public ResponseEntity<ScheduledCampaignResponse> pauseCampaign(
            @Parameter(description = "ID da campanha.") @PathVariable Long campaignId) {
        User currentUser = securityUtils.getAuthenticatedUser();
        ScheduledCampaign campaign = campaignService.pauseCampaign(campaignId, currentUser);
        return ResponseEntity.ok(ScheduledCampaignResponse.fromEntity(campaign));
    }

    @PostMapping("/{campaignId}/resume")
    @Operation(summary = "Retomar Campanha")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')")
    public ResponseEntity<ScheduledCampaignResponse> resumeCampaign(
            @Parameter(description = "ID da campanha.") @PathVariable Long campaignId) {
        User currentUser = securityUtils.getAuthenticatedUser();
        ScheduledCampaign campaign = campaignService.resumeCampaign(campaignId, currentUser);
        return ResponseEntity.ok(ScheduledCampaignResponse.fromEntity(campaign));
    }

    @DeleteMapping(value = "/{campaignId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancelar Campanha Agendada",
               description = "Cancela uma campanha que ainda está com status PENDENTE.")
    public ResponseEntity<ScheduledCampaignResponse> cancelCampaign(
            @Parameter(description = "ID da campanha a ser cancelada.", required = true) @PathVariable Long campaignId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        logger.info("Usuário ID {}: Recebida requisição para cancelar campanha ID {}.", currentUser.getId(), campaignId);
        ScheduledCampaign campaign = campaignService.cancelCampaign(campaignId, currentUser);
        return ResponseEntity.ok(ScheduledCampaignResponse.fromEntity(campaign));
    }
}
