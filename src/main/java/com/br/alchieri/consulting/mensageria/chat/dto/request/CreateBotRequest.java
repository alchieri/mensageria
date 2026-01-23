package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.time.LocalTime;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBotRequest {
    
    @NotBlank
    private String name;
    private BotTriggerType triggerType = BotTriggerType.ALWAYS;
    private LocalTime startTime;
    private LocalTime endTime;
    private String activeDays; // "1,2,3,4,5"
}
