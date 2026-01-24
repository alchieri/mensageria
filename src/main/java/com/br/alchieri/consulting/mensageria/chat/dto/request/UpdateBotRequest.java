package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.time.LocalTime;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dados para atualização das configurações do Bot")
public class UpdateBotRequest {
    
    @NotBlank
    @Schema(description = "Nome de identificação do Bot", example = "Atendimento Noturno")
    private String name;

    @Schema(description = "Tipo de gatilho para ativação", defaultValue = "ALWAYS")
    private BotTriggerType triggerType;

    @Schema(description = "Horário de início (se aplicável)", example = "18:00:00", type = "string", format = "time")
    private LocalTime startTime;

    @Schema(description = "Horário de fim (se aplicável)", example = "08:00:00", type = "string", format = "time")
    private LocalTime endTime;

    @Schema(description = "Dias da semana ativos (1=Dom, 7=Sab)", example = "2,3,4,5,6")
    private String activeDays;

    @Schema(description = "Define se o bot está ativo ou pausado", defaultValue = "true")
    private Boolean isActive; 
}
