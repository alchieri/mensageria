package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload genérico para eventos de callback internos da plataforma.")
public class InternalCallbackPayload<T> { // Usa genéricos para o payload real

    @Schema(description = "ID único do evento de callback.")
    private String eventId;

    @Schema(description = "Tipo do evento.", allowableValues = {"MESSAGE_STATUS", "CAMPAIGN_STATUS", "TEMPLATE_STATUS", "INCOMING_MESSAGE"})
    private String eventType;

    @Schema(description = "Timestamp da ocorrência do evento.")
    private LocalDateTime eventTimestamp;

    @Schema(description = "ID da empresa associada ao evento.")
    private Long companyId;
    
    @Schema(description = "ID do usuário associado ao evento (se aplicável).")
    private Long userId;

    @Schema(description = "O payload de dados específico do evento.")
    private T data; // Ex: MessageStatusPayload, CampaignStatusCallbackPayload, etc.
}