package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalDateTime;

import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informações detalhadas de um WhatsApp Flow.")
public class FlowResponse {

    private Long id;
    private String name;
    private String metaFlowId;
    private FlowStatus status;
    private JsonNode draftJsonDefinition;
    private JsonNode publishedJsonDefinition;
    private JsonNode validationErrors;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FlowResponse fromEntity(Flow entity, ObjectMapper objectMapper) {
        if (entity == null) return null;
        return FlowResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .metaFlowId(entity.getMetaFlowId())
                .status(entity.getStatus())
                .draftJsonDefinition(toJsonNode(entity.getDraftJsonDefinition(), objectMapper))
                .publishedJsonDefinition(toJsonNode(entity.getPublishedJsonDefinition(), objectMapper))
                .validationErrors(toJsonNode(entity.getValidationErrors(), objectMapper))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static JsonNode toJsonNode(String jsonString, ObjectMapper mapper) {
        if (jsonString == null) return null;
        try {
            return mapper.readTree(jsonString);
        } catch (Exception e) {
            return mapper.createObjectNode().put("parsing_error", e.getMessage());
        }
    }
}
