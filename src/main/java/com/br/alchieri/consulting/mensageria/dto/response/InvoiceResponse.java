package com.br.alchieri.consulting.mensageria.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ClientTemplateResponse;
import com.br.alchieri.consulting.mensageria.model.Invoice;
import com.br.alchieri.consulting.mensageria.model.InvoiceItem;
import com.br.alchieri.consulting.mensageria.model.enums.InvoiceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalhes completos de uma fatura, incluindo itens de linha.")
public class InvoiceResponse {
    private Long id;
    private YearMonth billingPeriod;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private InvoiceStatus status;
    private ClientTemplateResponse.CompanySummary company;
    private LocalDateTime createdAt;
    
    @Schema(description = "Lista de itens de linha que compõem a fatura.")
    private List<InvoiceItemResponse> items;

    public static InvoiceResponse fromEntity(Invoice entity) {
        if (entity == null) return null;
        return InvoiceResponse.builder()
                .id(entity.getId())
                .billingPeriod(entity.getBillingPeriod())
                .issueDate(entity.getIssueDate())
                .dueDate(entity.getDueDate())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .company(ClientTemplateResponse.CompanySummary.fromEntity(entity.getCompany()))
                .createdAt(entity.getCreatedAt())
                .items(entity.getItems().stream()
                              .map(InvoiceItemResponse::fromEntity)
                              .collect(Collectors.toList()))
                .build();
    }

    // DTO interno para os itens da fatura
    @Data
    @Builder
    @Schema(name = "InvoiceItemOutput", description = "Detalhe de um item de linha da fatura.")
    public static class InvoiceItemResponse {
        private String description;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;

        public static InvoiceItemResponse fromEntity(InvoiceItem entity) {
            if (entity == null) return null;
            return InvoiceItemResponse.builder()
                    .description(entity.getDescription())
                    .quantity(entity.getQuantity())
                    .unitPrice(entity.getUnitPrice())
                    .totalAmount(entity.getTotalAmount())
                    .build();
        }
    }
}
