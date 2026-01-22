package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.dto.callback.MessageStatusPayload;
import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalhes do status de uma mensagem enviada.")
public class MessageStatusResponse {

    @Schema(description = "ID da mensagem do WhatsApp (WAMID).")
    private String wamid;

    @Schema(description = "Status atual da mensagem (ex: SENT, DELIVERED, READ, FAILED).")
    private String status;

    @Schema(description = "Destinatário da mensagem.")
    private String recipient;

    @Schema(description = "Timestamp da última atualização de status.")
    private LocalDateTime lastUpdatedAt;

    @Schema(description = "Referência do conteúdo (ex: nome do template, início do texto).")
    private String contentReference;
    
    @Schema(description = "Detalhes do erro, se o status for FAILED.")
    private List<MessageStatusPayload.ErrorDetail> errors; // Reutiliza o DTO interno

    public static MessageStatusResponse fromLog(WhatsAppMessageLog log) {
        if (log == null) return null;
        return MessageStatusResponse.builder()
                .wamid(log.getWamid())
                .status(log.getStatus())
                .recipient(log.getRecipient())
                .lastUpdatedAt(log.getUpdatedAt())
                .contentReference(log.getContent()) // Ou um resumo
                .errors(MessageStatusPayload.parseErrors(log.getMetadata())) // Tenta parsear erros
                .build();
    }
}
