package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representa um chat ativo, com informações do último contato.")
public class ActiveChatResponse {

    @Schema(description = "Número de telefone do contato externo (cliente final).")
    private String contactPhoneNumber;

    @Schema(description = "Nome do contato (se disponível no banco).")
    private String contactName;

    @Schema(description = "Trecho da última mensagem trocada.")
    private String lastMessageSnippet;

    @Schema(description = "Direção da última mensagem (INCOMING ou OUTGOING).")
    private String lastMessageDirection;

    @Schema(description = "Timestamp da última mensagem trocada.")
    private LocalDateTime lastMessageTimestamp;

    @Schema(description = "Número de mensagens não lidas nesta conversa.")
    private long unreadCount;
}
