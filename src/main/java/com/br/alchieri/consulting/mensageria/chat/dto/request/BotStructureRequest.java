package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "Payload para salvar a estrutura completa (fluxo) do Bot.")
public class BotStructureRequest {
    
    @Schema(description = "ID temporário (ou real) do passo que será a raiz/início do fluxo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rootStepTempId;

    @NotEmpty
    @Valid
    @Schema(description = "Lista de todos os passos que compõem este bot.")
    private List<BotStepStructureDTO> steps;
}
