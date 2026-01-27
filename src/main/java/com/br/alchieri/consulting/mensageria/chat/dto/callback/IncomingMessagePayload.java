package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * Payload enviado para a URL de callback do cliente quando uma mensagem é recebida.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncomingMessagePayload {

    private String messageId;       // WAMID da mensagem recebida
    private String from;            // Número de quem enviou a mensagem
    private LocalDateTime timestamp;     // Quando a mensagem foi recebida (ou enviada pelo usuário)
    private String type;            // Tipo da mensagem (text, image, interactive, etc.)
    private String content;         // Conteúdo principal (texto, ID da mídia, ID do botão/linha)
    private String metadata;        // Dados adicionais (JSON com caption, filename, interactive details, etc.)
    private String profileName;     // Nome do perfil (se obtido) - Adicionar lógica para obter e incluir

    /**
     * Método helper para converter do Log.
     */
    public static IncomingMessagePayload fromLog(WhatsAppMessageLog log) {
         if (log == null || log.getDirection() != MessageDirection.INCOMING) {
             return null; // Ou lançar exceção
         }
        return IncomingMessagePayload.builder()
                .messageId(log.getWamid())
                .from(log.getChannelId()) // Usar Channel ID como "from"
                .timestamp(log.getCreatedAt()) // Usar timestamp de criação do log (que veio da msg)
                .type(log.getMessageType())
                .content(log.getContent())
                .metadata(log.getMetadata())
                // .profileName(...) // Adicionar se você armazenar/obter o nome do perfil
                .build();
    }
}
