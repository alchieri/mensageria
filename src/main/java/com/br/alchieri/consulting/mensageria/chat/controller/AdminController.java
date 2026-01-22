package com.br.alchieri.consulting.mensageria.chat.controller;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.response.TemplatePushResponse;
import com.br.alchieri.consulting.mensageria.chat.service.WhatsAppBusinessApiService;
import com.br.alchieri.consulting.mensageria.dto.request.AdminCreateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.AdminUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.CreateCompanyRequest;
import com.br.alchieri.consulting.mensageria.dto.request.UpdateCompanyRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.dto.response.CompanyDetailsResponse;
import com.br.alchieri.consulting.mensageria.dto.response.CompanyInfoResponse;
import com.br.alchieri.consulting.mensageria.dto.response.UserInfoResponse;
import com.br.alchieri.consulting.mensageria.dto.response.WhatsAppHealthStatusResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.CompanyService;
import com.br.alchieri.consulting.mensageria.service.HealthCheckService;
import com.br.alchieri.consulting.mensageria.service.UserService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

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
@RequestMapping("/api/v1/admin")
@Tag(name = "Platform Administration", description = "Endpoints para administradores BSP gerenciarem a plataforma.")
@SecurityRequirement(name = "bearerAuth")
//@PreAuthorize("hasRole('BSP_ADMIN')") // Nova role mais específica para o admin da plataforma
@RequiredArgsConstructor
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private final HealthCheckService healthCheckService;
    private final UserService userService;
    private final CompanyService companyService;
    private final WhatsAppBusinessApiService whatsAppBusinessApiService;
    private final SecurityUtils securityUtils;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(20);

    private User getBspAdmin() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // --- Endpoints de Gerenciamento de EMPRESAS ---

    @PostMapping(value = "/companies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Criar Nova Empresa Cliente (Admin)", description = "Registra uma nova empresa na plataforma.")
    public ResponseEntity<CompanyInfoResponse> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        logger.info("Admin {}: Criando nova empresa '{}'", getBspAdmin().getUsername(), request.getName());
        Company newCompany = companyService.createCompany(request, getBspAdmin());
        return ResponseEntity.status(HttpStatus.CREATED).body(CompanyInfoResponse.fromEntity(newCompany));
    }

    @GetMapping(value = "/companies", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Todas as Empresas (Admin)", description = "Retorna uma lista paginada de todas as empresas clientes.")
    public ResponseEntity<Page<CompanyInfoResponse>> getAllCompanies(@ParameterObject Pageable pageable) {
        logger.info("Admin {}: Listando todas as empresas", getBspAdmin().getUsername());
        Page<Company> companyPage = companyService.findAllCompanies(pageable);
        return ResponseEntity.ok(companyPage.map(CompanyInfoResponse::fromEntity));
    }

    @PutMapping(value = "/companies/{companyId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Atualizar Empresa (Admin)", description = "Atualiza os dados de uma empresa cliente existente.")
    public ResponseEntity<CompanyInfoResponse> updateCompany(
            @Parameter(description = "ID da empresa a ser atualizada.", required = true) @PathVariable Long companyId,
            @Valid @RequestBody UpdateCompanyRequest request) {
        logger.info("Admin {}: Atualizando empresa ID {}", getBspAdmin().getUsername(), companyId);
        Company updatedCompany = companyService.updateCompany(companyId, request, getBspAdmin());
        return ResponseEntity.ok(CompanyInfoResponse.fromEntity(updatedCompany));
    }

    // --- Endpoints de Gerenciamento de USUÁRIOS ---

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Todos os Usuários (Admin)", description = "Retorna uma lista paginada de todos os usuários da plataforma.")
    public ResponseEntity<Page<UserInfoResponse>> getAllUsers(@ParameterObject Pageable pageable) {
        logger.info("Admin {}: Listando todos os usuários", getBspAdmin().getUsername());
        return ResponseEntity.ok(userService.findAllUsers(pageable).map(UserInfoResponse::fromEntity));
    }

    @PutMapping(value = "/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Atualizar Usuário (Admin)", description = "Atualiza os dados de um usuário existente, incluindo sua empresa e roles.")
    public ResponseEntity<UserInfoResponse> updateUser(
            @Parameter(description = "ID do usuário a ser atualizado.", required = true) @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        logger.info("Admin {}: Atualizando usuário ID {}", getBspAdmin().getUsername(), userId);
        User updatedUser = userService.updateUserByAdmin(userId, request);
        return ResponseEntity.ok(UserInfoResponse.fromEntity(updatedUser));
    }

    @PostMapping(value = "/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Criar Novo Usuário (Admin)",
               description = "Cria um novo usuário na plataforma e, opcionalmente, o associa a uma empresa existente.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Usuário criado com sucesso",
                    content = @Content(schema = @Schema(implementation = UserInfoResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos ou usuário/email já existe.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Empresa não encontrada.", content = @Content)
    })
    public ResponseEntity<UserInfoResponse> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados do novo usuário.", required = true,
                    content = @Content(schema = @Schema(implementation = AdminCreateUserRequest.class)))
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
        logger.info("Admin '{}': Recebida requisição para criar novo usuário '{}'.", adminUsername, request.getUsername());

        // A exceção de negócio (usuário já existe) ou ResourceNotFound (empresa não existe)
        // serão capturadas pelo GlobalExceptionHandler.
        User newUser = userService.createUserByAdmin(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(UserInfoResponse.fromEntity(newUser));
    }

    @PostMapping("/companies/{companyId}/templates/push-to-meta")
    @Operation(summary = "Enviar Templates Locais para Meta (Admin)",
               description = "Busca templates salvos no banco local para uma empresa que não existem na Meta e os submete para aprovação. Use com cuidado.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Operação de envio concluída.",
                content = @Content(schema = @Schema(implementation = TemplatePushResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Empresa não encontrada.", content = @Content)
    })
    public Mono<ResponseEntity<TemplatePushResponse>> pushTemplatesToMeta(
            @Parameter(description = "ID da empresa alvo.", required = true) @PathVariable Long companyId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        logger.info("Admin ID {}: Iniciando envio de templates locais para empresa ID {}", currentUser.getId(), companyId);

        return whatsAppBusinessApiService.pushLocalTemplatesToMeta(companyId, currentUser)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/companies/{companyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar Detalhes da Empresa por ID (Admin)",
               description = "Retorna os detalhes completos de uma empresa cliente, incluindo usuários e plano de cobrança.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detalhes da empresa recuperados com sucesso.",
                    content = @Content(schema = @Schema(implementation = CompanyDetailsResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Empresa não encontrada.", content = @Content)
    })
    public ResponseEntity<CompanyDetailsResponse> getCompanyById(
            @Parameter(description = "ID da empresa a ser buscada.", required = true) @PathVariable Long companyId
    ) {
        logger.info("Admin {}: Buscando detalhes da empresa ID {}", securityUtils.getAuthenticatedUser().getUsername(), companyId);

        return companyService.findById(companyId)
                .map(company -> ResponseEntity.ok(CompanyDetailsResponse.fromEntity(company)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Buscar Usuário por ID (Admin)",
               description = "Retorna os detalhes de um usuário específico pelo seu ID.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detalhes do usuário recuperados com sucesso.",
                    content = @Content(schema = @Schema(implementation = UserInfoResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content)
    })
    public ResponseEntity<UserInfoResponse> getUserById(
            @Parameter(description = "ID do usuário a ser buscado.", required = true) @PathVariable Long userId
    ) {
        String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
        logger.info("Admin '{}': Buscando detalhes do usuário ID {}", adminUsername, userId);

        return userService.findById(userId)
                .map(user -> ResponseEntity.ok(UserInfoResponse.fromEntity(user)))
                .orElseGet(() -> {
                    logger.warn("Admin '{}': Usuário com ID {} não encontrado.", adminUsername, userId);
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping(value = "/companies/{companyId}/whatsapp-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verificar Saúde da Configuração WhatsApp (Admin)",
               description = "Busca em tempo real na API da Meta o status da WABA e do Número de Telefone associados a uma empresa.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status recuperado com sucesso.",
                    content = @Content(schema = @Schema(implementation = WhatsAppHealthStatusResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Empresa não encontrada.", content = @Content)
    })
    public ResponseEntity<?> getCompanyWhatsAppStatus(
            @Parameter(description = "ID da empresa a ser verificada.", required = true) @PathVariable Long companyId
    ) {
        User currentUser = securityUtils.getAuthenticatedUser();
        logger.info("Admin ID {}: Verificando status WhatsApp para a empresa ID {}", currentUser.getId(), companyId);

        try {
            // Busca a empresa primeiro
            Company company = companyService.findById(companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa com ID " + companyId + " não encontrada."));
            
            // Chama o serviço reativo e BLOQUEIA para obter o resultado
            WhatsAppHealthStatusResponse healthStatus = healthCheckService.checkWhatsAppConfigStatus(company)
                    .block(BLOCK_TIMEOUT); // Espera o resultado ou um timeout

            if (healthStatus == null) {
                 // Isso pode acontecer se o Mono do serviço completar vazio por algum motivo
                 throw new BusinessException("A verificação de status não retornou um resultado.");
            }

            return ResponseEntity.ok(healthStatus);

        } catch (ResourceNotFoundException e) {
             throw e; // Deixa o GlobalExceptionHandler retornar 404
        } catch (RuntimeException e) { // Captura timeout do block() e outros erros reativos
             logger.error("Erro inesperado (bloqueante) ao verificar status para empresa ID {}: {}", companyId, e.getMessage(), e);
             ApiResponse errorResponse = new ApiResponse(false, "Erro interno ou timeout ao verificar status: " + e.getMessage(), null);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
