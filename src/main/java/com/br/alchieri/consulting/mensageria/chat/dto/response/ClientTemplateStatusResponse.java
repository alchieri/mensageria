package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta com o status de um template submetido por um cliente através desta API.")
public class ClientTemplateStatusResponse {

    @Schema(description = "ID interno do registro do template do cliente.")
    private Long internalId;

    @Schema(description = "Nome do template.")
    private String templateName;

    @Schema(description = "Idioma do template.")
    private String language;

    @Schema(description = "Categoria do template.")
    private String category;

    @Schema(description = "Status atual do template (ex: PENDING_APPROVAL, APPROVED, REJECTED).")
    private String status;

    @Schema(description = "ID do template na plataforma Meta (se disponível).")
    private String metaTemplateId;

    @Schema(description = "Motivo da rejeição ou outra informação da Meta (se aplicável).")
    private String reason;

    @Schema(description = "Data de criação do registro.")
    private LocalDateTime createdAt;

    @Schema(description = "Data da última atualização do status.")
    private LocalDateTime lastUpdatedAt;

    public static ClientTemplateStatusResponse fromEntity(ClientTemplate entity) {
        if (entity == null) {
            return null;
        }
        return ClientTemplateStatusResponse.builder()
                .internalId(entity.getId())
                .templateName(entity.getTemplateName())
                .language(entity.getLanguage())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .metaTemplateId(entity.getMetaTemplateId())
                .reason(entity.getReason())
                .createdAt(entity.getCreatedAt())
                .lastUpdatedAt(entity.getUpdatedAt())
                .build();
    }
}
