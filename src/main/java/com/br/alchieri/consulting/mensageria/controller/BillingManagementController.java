package com.br.alchieri.consulting.mensageria.controller;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.InvoiceRepository;
import com.br.alchieri.consulting.mensageria.dto.request.CreateBillingPlanRequest;
import com.br.alchieri.consulting.mensageria.dto.response.BillingPlanResponse;
import com.br.alchieri.consulting.mensageria.dto.response.InvoiceResponse;
import com.br.alchieri.consulting.mensageria.dto.response.InvoiceSummaryResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.BillingPlan;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.Invoice;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/billing-plans")
@Tag(name = "Billing Management (Admin)", description = "Endpoints para administradores BSP gerenciarem planos de cobrança dos clientes.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('BSP_ADMIN')") // Protege todos os endpoints neste controller
@RequiredArgsConstructor
public class BillingManagementController {

        private static final Logger logger = LoggerFactory.getLogger(BillingManagementController.class);
        private final BillingService billingService;
        private final InvoiceRepository invoiceRepository;
        private final CompanyRepository companyRepository;
        private final SecurityUtils securityUtils;

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Criar ou Atualizar Plano de Cobrança (Admin)", description = "Cria um novo plano de cobrança para uma empresa ou atualiza um existente. Se o plano para a empresa já existir, ele será sobrescrito com os novos valores.")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plano de cobrança criado/atualizado com sucesso.", content = @Content(schema = @Schema(implementation = BillingPlanResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos ou empresa não encontrada.", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido (requer ROLE_BSP_ADMIN).", content = @Content)
        })
        public ResponseEntity<BillingPlanResponse> createOrUpdateBillingPlan(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados do plano de cobrança a ser criado ou atualizado.", required = true, content = @Content(schema = @Schema(implementation = CreateBillingPlanRequest.class))) @Valid @RequestBody CreateBillingPlanRequest request) {
                String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
                logger.info("Admin '{}': Recebida requisição para criar/atualizar plano de cobrança para empresa ID: {}",
                                adminUsername, request.getCompanyId());
                try {
                        BillingPlan plan = billingService.createOrUpdateCompanyBillingPlan(request);
                        return ResponseEntity.ok(BillingPlanResponse.fromEntity(plan));
                } catch (BusinessException e) {
                        // Deixa o GlobalExceptionHandler tratar e retornar 400
                        throw e;
                }
        }

        @GetMapping(value = "/company/{companyId}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Obter Plano de Cobrança da Empresa (Admin)", description = "Retorna os detalhes do plano de cobrança de uma empresa específica pelo ID da empresa.")
        @ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plano recuperado com sucesso.", content = @Content(schema = @Schema(implementation = BillingPlanResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plano ou empresa não encontrada.", content = @Content)
        })
        public ResponseEntity<BillingPlanResponse> getBillingPlanForCompany(
                        @Parameter(description = "ID da empresa.", required = true) @PathVariable Long companyId) {
                String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
                logger.info("Admin '{}': Buscando plano de cobrança para empresa ID: {}", adminUsername, companyId);
                return billingService.getBillingPlanByCompanyId(companyId)
                                .map(plan -> ResponseEntity.ok(BillingPlanResponse.fromEntity(plan)))
                                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Listar Todos os Planos de cobranças (Admin)", description = "Retorna uma lista paginada de todos os planos de cobrança da plataforma.")
        public ResponseEntity<Page<BillingPlanResponse>> getAllBillingPlans(@ParameterObject Pageable pageable) {
                String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
                logger.info("Admin '{}': Listando todos os Planos de cobranças", adminUsername);
                return ResponseEntity
                                .ok(billingService.findAllBillingPlans(pageable).map(BillingPlanResponse::fromEntity));
        }

        @GetMapping(value = "/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Listar Todas as Faturas (Admin)", description = "Retorna uma lista paginada de todas as faturas do sistema, com opção de filtro por empresa.")
        public ResponseEntity<Page<InvoiceSummaryResponse>> getAllInvoices(
                        @Parameter(description = "ID da empresa para filtrar.") @RequestParam(required = false) Optional<Long> companyId,
                        @ParameterObject Pageable pageable) {

                String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
                Page<Invoice> invoicePage;

                if (companyId.isPresent()) {
                        logger.info("Admin '{}': Listando faturas para a empresa ID {}.", adminUsername,
                                        companyId.get());
                        Company company = companyRepository.findById(companyId.get())
                                        .orElseThrow(() -> new com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException(
                                                        "Empresa não encontrada."));
                        invoicePage = invoiceRepository.findByCompanyOrderByIssueDateDesc(company, pageable);
                } else {
                        logger.info("Admin '{}': Listando todas as faturas.", adminUsername);
                        invoicePage = invoiceRepository.findAllWithCompany(pageable);
                }
                return ResponseEntity.ok(invoicePage.map(InvoiceSummaryResponse::fromEntity));
        }

        @GetMapping(value = "/invoices/{invoiceId}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(summary = "Obter Detalhes de Fatura (Admin)", description = "Retorna os detalhes completos de qualquer fatura pelo seu ID.")
        public ResponseEntity<InvoiceResponse> getInvoiceDetails(
                        @Parameter(description = "ID da fatura a ser consultada.", required = true) @PathVariable Long invoiceId) {
                String adminUsername = securityUtils.getAuthenticatedUser().getUsername();
                logger.info("Admin '{}': Buscando detalhes da fatura ID {}.", adminUsername, invoiceId);

                return invoiceRepository.findById(invoiceId)
                                .map(invoice -> ResponseEntity.ok(InvoiceResponse.fromEntity(invoice)))
                                .orElseGet(() -> ResponseEntity.notFound().build());
        }
}
