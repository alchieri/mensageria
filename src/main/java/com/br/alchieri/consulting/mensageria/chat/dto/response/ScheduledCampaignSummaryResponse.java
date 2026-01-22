package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumo de uma campanha de mensagens agendada para listas.")
public class ScheduledCampaignSummaryResponse {

    private Long id;
    private String campaignName;
    private String templateName;
    private LocalDateTime scheduledAt;
    private ScheduledCampaign.CampaignStatus status;
    private Integer totalMessages;
    private Integer sentMessages;
    private Integer failedMessages;
    private LocalDateTime createdAt;

    public static ScheduledCampaignSummaryResponse fromEntity(ScheduledCampaign entity) {
        if (entity == null) return null;
        return ScheduledCampaignSummaryResponse.builder()
                .id(entity.getId())
                .campaignName(entity.getCampaignName())
                .templateName(entity.getTemplateName())
                .scheduledAt(entity.getScheduledAt())
                .status(entity.getStatus())
                .totalMessages(entity.getTotalMessages())
                .sentMessages(entity.getSentMessages())
                .failedMessages(entity.getFailedMessages())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
