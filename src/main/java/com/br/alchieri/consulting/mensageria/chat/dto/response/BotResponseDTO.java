package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.time.LocalTime;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotTriggerType;

import lombok.Data;

@Data
public class BotResponseDTO {
    private Long id;
    private String name;
    private boolean isActive;
    private BotTriggerType triggerType;
    private LocalTime startTime;
    private LocalTime endTime;
    private String activeDays;
    private Long rootStepId; // Apenas o ID para não carregar a árvore inteira na lista
}
