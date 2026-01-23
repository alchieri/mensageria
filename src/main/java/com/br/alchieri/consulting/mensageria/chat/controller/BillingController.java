package com.br.alchieri.consulting.mensageria.chat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.response.CompanyUsageResponse;
import com.br.alchieri.consulting.mensageria.dto.response.InvoiceResponse;
import com.br.alchieri.consulting.mensageria.dto.response.InvoiceSummaryResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.Invoice;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.repository.InvoiceRepository;
import com.br.alchieri.consulting.mensageria.service.BillingService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing (Client View)", description = "Endpoints para clientes consultarem seu uso e informações de plano.")
@SecurityRequirement(name = "bearerAuth") // Requer autenticação JWT para todos os endpoints aqui
@RequiredArgsConstructor
public class BillingController {

    private static final Logger logger = LoggerFactory.getLogger(BillingController.class);
    private final BillingService billingService;
    private final InvoiceRepository invoiceRepository;
    
    private final SecurityUtils securityUtils;

    @GetMapping(value = "/my-usage", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar Meu Uso do Plano",
               description = "Retorna o uso atual do plano de cobrança da empresa do cliente autenticado, incluindo limites e custos estimados de excedentes.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Informações de uso recuperadas com sucesso.",
                    content = @Content(schema = @Schema(implementation = CompanyUsageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Não Autorizado.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plano de cobrança não encontrado para a empresa do cliente.", content = @Content)
    })
    public ResponseEntity<CompanyUsageResponse> getMyUsage() {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();

        if (currentCompany == null) {
            logger.warn("Usuário ID {} tentou consultar uso sem estar associado a uma empresa.", currentUser.getId());
            throw new BusinessException("Usuário não está associado a uma empresa para consultar um plano de cobrança.");
        }

        logger.info("Empresa ID {}: Recebida requisição para consultar uso do plano.", currentCompany.getId());

        // O getClientUsage agora é chamado com a Company do usuário autenticado
        return billingService.getClientCompanyUsage(currentCompany)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    logger.warn("Nenhum plano de cobrança encontrado para a empresa ID: {}", currentCompany.getId());
                    return ResponseEntity.notFound().build();
                });
    }

    @GetMapping(value = "/my-invoices", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar Minhas Faturas",
               description = "Retorna o histórico paginado de faturas da empresa do cliente autenticado.")
    public ResponseEntity<Page<InvoiceSummaryResponse>> getMyInvoices(@ParameterObject Pageable pageable) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        if (currentCompany == null) {
            throw new BusinessException("Usuário não associado a uma empresa.");
        }
        logger.info("Empresa ID {}: Listando faturas.", currentCompany.getId());
        
        Page<Invoice> invoicePage = invoiceRepository.findByCompanyOrderByIssueDateDesc(currentCompany, pageable);
        return ResponseEntity.ok(invoicePage.map(InvoiceSummaryResponse::fromEntity));
    }
    
    @GetMapping(value = "/my-invoices/{invoiceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obter Detalhes de uma Fatura",
               description = "Retorna os detalhes completos de uma fatura específica da empresa do cliente autenticado.")
    public ResponseEntity<InvoiceResponse> getMyInvoiceDetails(
            @Parameter(description = "ID da fatura a ser consultada.", required = true) @PathVariable Long invoiceId) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company currentCompany = currentUser.getCompany();
        if (currentCompany == null) {
            throw new BusinessException("Usuário não associado a uma empresa.");
        }
        logger.info("Empresa ID {}: Buscando detalhes da fatura ID {}.", currentCompany.getId(), invoiceId);

        return invoiceRepository.findByIdAndCompany(invoiceId, currentCompany)
                .map(invoice -> ResponseEntity.ok(InvoiceResponse.fromEntity(invoice)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
