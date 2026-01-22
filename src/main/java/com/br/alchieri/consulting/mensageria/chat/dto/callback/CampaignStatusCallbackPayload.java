package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload enviado para a URL de callback do cliente quando o status de uma campanha é atualizado.")
public class CampaignStatusCallbackPayload {

    @Schema(description = "ID único da campanha (do seu sistema).")
    private Long campaignId;

    @Schema(description = "Nome da campanha.")
    private String campaignName;

    @Schema(description = "Novo status da campanha (ex: PROCESSING, COMPLETED, CANCELED).")
    private ScheduledCampaign.CampaignStatus newStatus;

    @Schema(description = "Timestamp da atualização do status.")
    private LocalDateTime statusTimestamp;

    @Schema(description = "Número total de mensagens na campanha.")
    private Integer totalMessages;

    // Adicionar outros campos se forem úteis para o cliente no callback
    // private Integer sentMessages;
    // private Integer failedMessages;

    public static CampaignStatusCallbackPayload fromEntity(ScheduledCampaign entity) {
        if (entity == null) {
            return null;
        }
        return CampaignStatusCallbackPayload.builder()
                .campaignId(entity.getId())
                .campaignName(entity.getCampaignName())
                .newStatus(entity.getStatus())
                .statusTimestamp(entity.getUpdatedAt()) // Usa o timestamp da última atualização
                .totalMessages(entity.getTotalMessages())
                .build();
    }
}
