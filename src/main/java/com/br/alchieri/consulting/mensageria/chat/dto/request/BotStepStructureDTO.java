package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class BotStepStructureDTO {
    
    @Schema(description = "ID temporário gerado pelo front (UUID) para linkar passos novos.", example = "temp-1234")
    private String tempId;

    @Schema(description = "ID real do banco (se for edição de passo existente).", nullable = true)
    private Long id;

    private String title;
    private BotStepType stepType;
    private String content;
    private String metadata;

    private List<BotOptionStructureDTO> options;
}
