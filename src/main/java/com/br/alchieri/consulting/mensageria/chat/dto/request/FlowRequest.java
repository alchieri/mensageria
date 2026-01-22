package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Dados para criar um novo WhatsApp Flow.")
public class FlowRequest {
    @NotBlank
    @Schema(description = "Nome amigável para identificar o Flow no sistema.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Formulário de Agendamento")
    private String name;

    @NotEmpty
    @Schema(description = "Lista de categorias para o Flow.",
            example = "[\"LEAD_GENERATION\"]",
            allowableValues = {"SIGN_UP", "SIGN_IN", "APPOINTMENT_BOOKING", "LEAD_GENERATION", "CONTACT_US", "CUSTOMER_SUPPORT", "SURVEY", "OTHER"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> categories;

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+$", message = "A versão do JSON do Flow deve estar no formato 'major.minor' (ex: '5.0').")
    @Schema(description = "Versão do Flow JSON a ser usada.", example = "5.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jsonVersion;

    @Pattern(regexp = "^\\d+\\.\\d+$", message = "A versão da API de Dados deve estar no formato 'major.minor' (ex: '3.0').")
    @Schema(description = "Versão da API de Dados (obrigatório se o Flow usa um endpoint).", example = "3.0")
    private String dataApiVersion;
    
    @Schema(description = "A URL do Ponto de Extremidade (Endpoint) do Flow (obrigatório se o Flow usa um).")
    private String endpointUri;

    @NotNull
    @Schema(description = "A definição completa do Flow em formato JSON, sem os campos 'name' e 'routing_model'.", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode jsonDefinition;
}
