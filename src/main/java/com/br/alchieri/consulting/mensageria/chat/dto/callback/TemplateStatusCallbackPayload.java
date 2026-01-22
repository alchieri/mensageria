package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload enviado para a URL de callback do cliente quando o status de um template é atualizado.")
public class TemplateStatusCallbackPayload {

    @Schema(description = "ID interno (do seu sistema) do registro do template.")
    private Long internalTemplateId;

    @Schema(description = "Nome do template que foi atualizado.")
    private String templateName;

    @Schema(description = "Idioma do template.")
    private String language;

    @Schema(description = "Novo status do template (ex: APPROVED, REJECTED).")
    private String newStatus;

    @Schema(description = "Motivo da rejeição ou outra informação da Meta (se aplicável).")
    private String reason;

    @Schema(description = "Data e hora da última atualização de status.")
    private LocalDateTime statusTimestamp;

    public static TemplateStatusCallbackPayload fromClientTemplate(ClientTemplate ct) {
        if (ct == null) return null;
        return TemplateStatusCallbackPayload.builder()
                .internalTemplateId(ct.getId())
                .templateName(ct.getTemplateName())
                .language(ct.getLanguage())
                .newStatus(ct.getStatus())
                .reason(ct.getReason())
                .statusTimestamp(ct.getUpdatedAt())
                .build();
    }
}
