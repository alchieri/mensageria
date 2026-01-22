package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload enviado para a URL de callback do cliente com os dados recebidos de um WhatsApp Flow.")
@Slf4j
public class FlowDataCallbackPayload {

    @Schema(description = "ID do registro dos dados do Flow (do seu sistema).")
    private Long flowDataId;

    @Schema(description = "ID do Flow (da Meta) que originou os dados.")
    private String metaFlowId;
    
    @Schema(description = "ID do contato (do seu sistema) que enviou os dados.")
    private Long contactId;
    
    @Schema(description = "WA ID (número de telefone) do usuário do WhatsApp que enviou os dados.")
    private String senderWaId;

    @Schema(description = "Os dados do formulário do Flow, em formato JSON.")
    private JsonNode data;

    @Schema(description = "Timestamp do recebimento dos dados.")
    private LocalDateTime receivedAt;

    public static FlowDataCallbackPayload fromEntity(FlowData entity, ObjectMapper objectMapper) {
        if (entity == null) return null;

        JsonNode dataNode = null;
        try {
            // Converte a string JSON do banco de volta para um objeto JSON
            dataNode = objectMapper.readTree(entity.getDecryptedJsonResponse());
        } catch (Exception e) {
            log.error("Erro ao fazer parse do decryptedJsonResponse para o callback do FlowData ID {}", entity.getId(), e);
            // Opcional: Enviar o JSON bruto como string se o parse falhar
        }

        return FlowDataCallbackPayload.builder()
                .flowDataId(entity.getId())
                .metaFlowId(entity.getFlow() != null ? entity.getFlow().getMetaFlowId() : null)
                .contactId(entity.getContact() != null ? entity.getContact().getId() : null)
                .senderWaId(entity.getSenderWaId())
                .data(dataNode)
                .receivedAt(entity.getReceivedAt())
                .build();
    }
}
