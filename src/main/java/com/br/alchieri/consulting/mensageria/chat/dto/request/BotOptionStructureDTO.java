package com.br.alchieri.consulting.mensageria.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class BotOptionStructureDTO {
    
    @Schema(description = "ID real da opção (se já existir).")
    private Long id;

    private String keyword;
    private String label;
    private Integer sequence;
    private boolean isHandoff;

    @Schema(description = "Para onde essa opção leva? Use o ID temporário do passo destino.")
    private String targetStepTempId;

    @Schema(description = "Para onde essa opção leva? Use o ID real (se souber).")
    private Long targetStepId;
}
