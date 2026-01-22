package com.br.alchieri.consulting.mensageria.chat.dto.callback;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowStatusCallbackPayload {
    private Long internalFlowId;
    private String metaFlowId;
    private String flowName;
    private FlowStatus newStatus;
    private LocalDateTime statusTimestamp;

    public static FlowStatusCallbackPayload fromEntity(Flow entity) {
        return FlowStatusCallbackPayload.builder()
                .internalFlowId(entity.getId())
                .metaFlowId(entity.getMetaFlowId())
                .flowName(entity.getName())
                .newStatus(entity.getStatus())
                .statusTimestamp(entity.getUpdatedAt())
                .build();
    }
}
