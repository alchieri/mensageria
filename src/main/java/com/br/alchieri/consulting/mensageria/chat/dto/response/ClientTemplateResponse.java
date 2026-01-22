package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representação de um template de mensagem armazenado no sistema, incluindo informações da empresa proprietária.")
public class ClientTemplateResponse {

    private Long id;
    private String templateName;
    private String language;
    private String category;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Schema(description = "Informações da empresa proprietária deste template (visível para admins).")
    private CompanySummary company; // <<< Objeto aninhado com resumo da empresa

    public static ClientTemplateResponse fromEntity(ClientTemplate entity) {
        if (entity == null) return null;
        return ClientTemplateResponse.builder()
                .id(entity.getId())
                .templateName(entity.getTemplateName())
                .language(entity.getLanguage())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .reason(entity.getReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .company(CompanySummary.fromEntity(entity.getCompany())) // <<< Mapear empresa
                .build();
    }

    // DTO interno para o resumo da empresa
    @Data
    @Builder
    @Schema(name = "CompanySummaryOutput", description = "Informações resumidas de uma empresa.")
    public static class CompanySummary {
        private Long id;
        private String name;

        public static CompanySummary fromEntity(Company company) {
            if (company == null) return null;
            return CompanySummary.builder()
                    .id(company.getId())
                    .name(company.getName())
                    .build();
        }
    }
}
