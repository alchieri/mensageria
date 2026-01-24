package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Requisição para criar um Bot completo (Metadados + Estrutura de Fluxo) em uma única chamada.")
public class CreateBotWithStructureRequest extends CreateBotRequest {

    @Schema(description = "ID temporário do passo que será a raiz do fluxo.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rootStepTempId;

    @Valid
    @Schema(description = "Lista de passos e conexões que compõem o bot.")
    private List<BotStepStructureDTO> steps;
}
