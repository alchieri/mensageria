package com.br.alchieri.consulting.mensageria.chat.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Dados para atualizar a definição JSON de um WhatsApp Flow existente.")
public class FlowJsonUpdateRequest {

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+$", message = "A versão do JSON do Flow deve estar no formato 'major.minor' (ex: '5.0').")
    @Schema(description = "Nova versão do Flow JSON.", example = "5.1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jsonVersion;

    @NotNull
    @Schema(description = "A nova definição completa do Flow em formato JSON.", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode jsonDefinition;
}
