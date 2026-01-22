package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.dto.response.ClientTemplateResponse.CompanySummary;
import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações detalhadas de um template de mensagem armazenado no sistema, incluindo o JSON dos componentes.")
public class ClientTemplateDetailsResponse {

    private Long id;
    private String templateName;
    private String language;
    private String category;
    private String status;
    private String reason;
    private String metaTemplateId; // ID da Meta
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Schema(description = "JSON bruto da definição dos componentes do template. Contém as variáveis e a estrutura.")
    private String componentsJson; // <<< Campo adicionado

    @Schema(description = "Informações da empresa proprietária deste template (visível para admins).")
    private ClientTemplateResponse.CompanySummary company; // Reutiliza DTO interno

    public static ClientTemplateDetailsResponse fromEntity(ClientTemplate entity) {
        if (entity == null) return null;
        return ClientTemplateDetailsResponse.builder()
                .id(entity.getId())
                .templateName(entity.getTemplateName())
                .language(entity.getLanguage())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .reason(entity.getReason())
                .metaTemplateId(entity.getMetaTemplateId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .componentsJson(entity.getComponentsJson()) // <<< Mapeia o JSON dos componentes
                .company(ClientTemplateResponse.CompanySummary.fromEntity(entity.getCompany()))
                .build();
    }
}
