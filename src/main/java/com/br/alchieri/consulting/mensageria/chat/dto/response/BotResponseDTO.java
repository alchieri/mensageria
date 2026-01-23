package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalTime;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Objeto de resposta representando um Bot.")
public class BotResponseDTO {
    
    @Schema(description = "ID único do Bot.")
    private Long id;

    @Schema(description = "Nome do Bot.")
    private String name;

    @Schema(description = "Indica se o bot está ativo no momento.")
    private boolean isActive;

    @Schema(description = "Regra de gatilho configurada.")
    private BotTriggerType triggerType;

    @Schema(description = "Horário de abertura.")
    private LocalTime startTime;

    @Schema(description = "Horário de fechamento.")
    private LocalTime endTime;

    @Schema(description = "Dias da semana ativos.")
    private String activeDays;

    @Schema(description = "ID do passo inicial (Raiz) deste bot.")
    private Long rootStepId; 
}
