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
@Schema(description = "Detalhes de uma campanha de mensagens agendada.")
public class ScheduledCampaignResponse {

    private Long id;
    private Long companyId;
    private Long createdByUserId;
    private String campaignName;
    private String templateName;
    private String languageCode;
    private LocalDateTime scheduledAt;
    private ScheduledCampaign.CampaignStatus status;
    private Integer totalMessages;
    private Integer sentMessages;
    private Integer failedMessages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScheduledCampaignResponse fromEntity(ScheduledCampaign entity) {
        if (entity == null) return null;
        return ScheduledCampaignResponse.builder()
                .id(entity.getId())
                .companyId(entity.getCompany().getId())
                .createdByUserId(entity.getCreatedByUser().getId())
                .campaignName(entity.getCampaignName())
                .templateName(entity.getTemplateName())
                .languageCode(entity.getLanguageCode())
                .scheduledAt(entity.getScheduledAt())
                .status(entity.getStatus())
                .totalMessages(entity.getTotalMessages())
                .sentMessages(entity.getSentMessages())
                .failedMessages(entity.getFailedMessages())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
