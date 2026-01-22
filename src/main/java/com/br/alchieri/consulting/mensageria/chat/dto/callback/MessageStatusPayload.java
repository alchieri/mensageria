package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.br.alchieri.consulting.mensageria.chat.model.WhatsAppMessageLog;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MessageDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Payload enviado para a URL de callback do cliente quando o status de uma mensagem enviada é atualizado.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j // Adicionar para log dentro do método estático
public class MessageStatusPayload {

    private String messageId;   // WAMID da mensagem original enviada
    private String status;      // Novo status (SENT, DELIVERED, READ, FAILED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp; // Quando o status foi atualizado
    private String recipient;   // Para quem a mensagem foi enviada
    private List<ErrorDetail> errors; // Detalhes do erro se status for FAILED

    /**
     * Método helper para converter do Log.
     */
    public static MessageStatusPayload fromLog(WhatsAppMessageLog log) {
        if (log == null || log.getDirection() != MessageDirection.OUTGOING) {
            return null; // Ou lançar exceção
        }
        return MessageStatusPayload.builder()
                .messageId(log.getWamid())
                .status(log.getStatus())
                .timestamp(log.getUpdatedAt()) // Usar timestamp da última atualização
                .recipient(log.getRecipient())
                .errors(parseErrors(log.getMetadata())) // Tenta parsear erros do metadata
                .build();
    }

    // Método helper para parsear JSON de erro do metadata
    public static List<ErrorDetail> parseErrors(String metadataJson) {
        if (metadataJson == null || !metadataJson.startsWith("[")) { // Verifica se parece um array JSON
            return null;
        }
        try {
            // Usar ObjectMapper estático ou injetado
             ObjectMapper mapper = new ObjectMapper();
             // A API da Meta retorna um array de erros
             List<Map<String, Object>> errorMaps = mapper.readValue(metadataJson, new TypeReference<List<Map<String, Object>>>() {});
             return errorMaps.stream().map(errorMap ->
                 ErrorDetail.builder()
                     .code( (Integer) errorMap.getOrDefault("code", 0) ) // Cuidado com tipos
                     .title( (String) errorMap.getOrDefault("title", "N/A") )
                     .message( (String) errorMap.getOrDefault("message", null) )
                     .details( extractErrorDetails(errorMap.get("error_data")) ) // Extrair detalhes internos
                     .build()
             ).toList();

        } catch (Exception e) {
            log.error("Erro ao parsear metadados de erro do status: {}", e.getMessage()); // Usar logger Slf4j
            return null;
        }
    }

    private static String extractErrorDetails(Object errorData) {
        if (errorData instanceof Map) {
            Object details = ((Map<?, ?>) errorData).get("details");
            return details instanceof String ? (String) details : null;
        }
        return null;
    }


    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private int code;
        private String title;
        private String message; // Mensagem principal do erro
        private String details; // Detalhes específicos dentro de error_data
    }
}
