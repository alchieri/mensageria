package com.br.alchieri.consulting.mensageria.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.HealthCheckService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/health-check")
@Tag(name = "Health Check", description = "Endpoints para verificação de status e saúde das integrações.")
@SecurityRequirement(name = "bearerAuth") // Requer autenticação JWT
@RequiredArgsConstructor
public class HealthCheckController {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);
    private final HealthCheckService healthCheckService;
    private final SecurityUtils securityUtils;

    @GetMapping(value = "/whatsapp-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verificar Saúde da Configuração WhatsApp",
               description = "Busca em tempo real na API da Meta o status da WABA e do Número de Telefone associados à empresa do usuário autenticado.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status recuperado com sucesso.",
                    content = @Content(schema = @Schema(implementation = WhatsAppHealthStatusResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Usuário não associado a uma empresa ou empresa sem configuração Meta.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno do servidor.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<WhatsAppHealthStatusResponse> getMyCompanyWhatsAppStatus() {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();

        // Valida se o usuário tem uma empresa associada
        if (currentCompany == null) {
            logger.warn("Usuário ID {} tentou verificar status sem estar associado a uma empresa.", currentUser.getId());
            // Lança exceção que será pega pelo GlobalExceptionHandler
            throw new BusinessException("Usuário não está associado a nenhuma empresa.");
        }

        logger.info("Empresa ID {}: Verificando status da configuração WhatsApp.", currentCompany.getId());
        WhatsAppHealthStatusResponse healthStatus = healthCheckService.checkWhatsAppConfigStatus(currentCompany);
            
        if (healthStatus == null) {
            throw new BusinessException("A verificação de status não retornou um resultado.");
        }

        return ResponseEntity.ok(healthStatus);
    }
}
