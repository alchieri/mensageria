package com.br.alchieri.consulting.mensageria.chat.dto.response;

import lombok.Data;

@Data
public class BotOptionDTO {
    private Long id;
    private String keyword;
    private String label;
    private Integer sequence;
    private boolean isHandoff;
    private Long targetStepId; // Importante: Retornamos apenas o ID do alvo
}
