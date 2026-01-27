package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representa um registro de log de mensagem do WhatsApp.")
public class MessageLogResponse {

    private Long id;
    private String wamid;
    private Long userId; // ID do usuário do sistema que enviou a mensagem
    private MessageDirection direction;
    private String senderPhoneNumber;
    private String channelId;
    private String recipient;
    private String messageType;
    private String content;
    private String metadata;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MessageLogResponse fromEntity(WhatsAppMessageLog log) {
        if (log == null) return null;
        return MessageLogResponse.builder()
                .id(log.getId())
                .wamid(log.getWamid())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .direction(log.getDirection())
                .senderPhoneNumber(log.getSenderPhoneNumber())
                .channelId(log.getChannelId())
                .recipient(log.getRecipient())
                .messageType(log.getMessageType())
                .content(log.getContent())
                .metadata(log.getMetadata())
                .status(log.getStatus())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}
