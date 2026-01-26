package com.br.alchieri.consulting.mensageria.chat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.UpdateCallbacksRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.dto.response.CompanyInfoResponse;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.CompanyService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/company/config")
@Tag(name = "Company Configuration", description = "Endpoints para administradores de empresa gerenciarem suas configurações.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('COMPANY_ADMIN') or hasRole('BSP_ADMIN')") // Permite admin da empresa ou do BSP
@RequiredArgsConstructor
public class CompanyConfigController {

    private static final Logger logger = LoggerFactory.getLogger(CompanyConfigController.class);
    private final CompanyService companyService;
    private final SecurityUtils securityUtils;

    @PutMapping(value = "/callbacks", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Atualizar URLs de Callback",
               description = "Atualiza as URLs de callback para a empresa do usuário autenticado. Envie apenas os campos que deseja alterar.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URLs de callback atualizadas com sucesso.",
            content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CompanyInfoResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos.",
            content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<CompanyInfoResponse> updateCallbacks(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Novas URLs de callback. Campos não fornecidos não serão alterados.", required = true)
            @Valid @RequestBody UpdateCallbacksRequest request
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company companyToUpdate = currentUser.getCompany(); // Pega a empresa do usuário logado

        // Um BSP_ADMIN pode querer atualizar para outra empresa, mas este endpoint é para auto-serviço.
        // Para admin, usaria o AdminController.
        if (companyToUpdate == null) {
            throw new com.br.alchieri.consulting.mensageria.exception.BusinessException("Usuário não associado a uma empresa.");
        }

        logger.info("Usuário ID {}: Atualizando callbacks para a empresa ID {}", currentUser.getId(), companyToUpdate.getId());
        Company updatedCompany = companyService.updateCompanyCallbacks(companyToUpdate, request);
        
        // Retorna os dados atualizados da empresa
        return ResponseEntity.ok(CompanyInfoResponse.fromEntity(updatedCompany));
    }

    @PostMapping("/sync-business-id")
    @Operation(summary = "Sincronizar Meta Business ID", 
               description = "Consulta a API da Meta usando o WABA ID configurado para encontrar e vincular automaticamente o ID do Gerenciador de Negócios.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sincronização realizada com sucesso"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "WABA ID ausente ou erro na API da Meta")
    })
    public ResponseEntity<ApiResponse> syncBusinessId() {
        User user = securityUtils.getAuthenticatedUser();
        
        Company updatedCompany = companyService.syncMetaBusinessId(user.getCompany().getId());
        
        int bmCount = (updatedCompany.getBusinessManagers() != null) ? updatedCompany.getBusinessManagers().size() : 0;
        
        return ResponseEntity.ok(new ApiResponse(
            true, 
            "Sincronização realizada com sucesso. " + bmCount + " Business Managers encontrados e vinculados.", 
            updatedCompany // O JSON de retorno incluirá a lista "businessManagers"
        ));
    }
}
