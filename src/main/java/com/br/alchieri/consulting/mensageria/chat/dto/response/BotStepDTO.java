package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;

import lombok.Data;

@Data
public class BotStepDTO {
    private Long id;
    private String title;
    private BotStepType stepType;
    private String content;
    private String metadata;
    private List<BotOptionDTO> options;
}
