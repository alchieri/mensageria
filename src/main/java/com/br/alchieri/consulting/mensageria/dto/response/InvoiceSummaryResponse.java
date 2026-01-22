package com.br.alchieri.consulting.mensageria.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ClientTemplateResponse;
import com.br.alchieri.consulting.mensageria.model.Invoice;
import com.br.alchieri.consulting.mensageria.model.enums.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumo de uma fatura para listas.")
public class InvoiceSummaryResponse {
    @Schema(description = "ID da fatura.")
    private Long id;

    @Schema(description = "Período de faturamento (Ano-Mês).", example = "2026-01")
    private YearMonth billingPeriod;

    @Schema(description = "Data de emissão da fatura.")
    private LocalDate issueDate;

    @Schema(description = "Data de vencimento da fatura.")
    private LocalDate dueDate;

    @Schema(description = "Valor total da fatura.")
    private BigDecimal totalAmount;

    @Schema(description = "Status atual da fatura (PENDING, PAID, OVERDUE, CANCELED).")
    private InvoiceStatus status;
    
    @Schema(description = "Resumo da empresa faturada (visível para admins).")
    private ClientTemplateResponse.CompanySummary company; // Reutiliza o DTO de resumo

    public static InvoiceSummaryResponse fromEntity(Invoice entity) {
        if (entity == null) return null;
        return InvoiceSummaryResponse.builder()
                .id(entity.getId())
                .billingPeriod(entity.getBillingPeriod())
                .issueDate(entity.getIssueDate())
                .dueDate(entity.getDueDate())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .company(ClientTemplateResponse.CompanySummary.fromEntity(entity.getCompany()))
                .build();
    }
}
