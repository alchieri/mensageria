package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.time.LocalTime;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para criação de um novo Bot.")
public class CreateBotRequest {
    
    @NotBlank
    @Schema(description = "Nome de identificação do Bot.", example = "Atendimento Principal")
    private String name;

    @Schema(description = "Tipo de gatilho para ativação do bot.", defaultValue = "ALWAYS")
    private BotTriggerType triggerType = BotTriggerType.ALWAYS;
    
    @Schema(description = "Horário de início de funcionamento (se triggerType for HORARIO).", example = "08:00:00", type = "string", format = "time")
    private LocalTime startTime;

    @Schema(description = "Horário de fim de funcionamento (se triggerType for HORARIO).", example = "18:00:00", type = "string", format = "time")
    private LocalTime endTime;

    @Schema(description = "Dias da semana ativos separados por vírgula (1=Domingo, 7=Sábado).", example = "2,3,4,5,6")
    private String activeDays;
}
