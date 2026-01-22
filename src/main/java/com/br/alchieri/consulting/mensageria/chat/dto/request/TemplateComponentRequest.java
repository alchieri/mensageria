package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Representa um componente (header, body, button) de um template a ser enviado.")
public class TemplateComponentRequest {

    @NotBlank(message = "O tipo do componente é obrigatório (header, body, footer, buttons).")
    @Schema(description = "Tipo do componente.", allowableValues = {"header", "body", "button"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String type; // "header", "body", "buttons"

    // Parâmetros para substituir {{1}}, {{2}}...
    @Valid
    @Size(max = 10, message = "Número máximo de parâmetros por componente é 10.") // Ajuste se o limite da Meta for diferente
    @Schema(description = "Lista de parâmetros para este componente.")
    private List<TemplateParameterRequest> parameters;

    // Específico para botões
    @Schema(description = "Subtipo do componente (relevante para 'button').", allowableValues = {"quick_reply", "url"})
    private String sub_type; // "quick_reply", "url"

    @Schema(description = "Índice do botão (relevante para 'button', formato string '0', '1', etc.).", example = "0")
    private String index; // "0", "1", ... para botões de URL

    // Outros campos podem ser necessários dependendo do tipo (ex: format para header)
}
