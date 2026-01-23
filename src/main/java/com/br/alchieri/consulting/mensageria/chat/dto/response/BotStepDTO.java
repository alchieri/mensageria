package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.br.alchieri.consulting.mensageria.chat.model.enums.BotStepType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Representa um passo (nó) do fluxo do Bot.")
public class BotStepDTO {
    
    @Schema(description = "ID do passo.")
    private Long id;

    @Schema(description = "Título interno para organização.", example = "Menu Principal")
    private String title;

    @Schema(description = "Tipo do passo (TEXT, FLOW, TEMPLATE, etc).", example = "TEXT")
    private BotStepType stepType;

    @Schema(description = "Conteúdo principal. Para TEXT é a mensagem, para TEMPLATE é o nome do template, para FLOW é o ID do flow.", example = "Olá, bem-vindo ao nosso atendimento!")
    private String content;

    @Schema(description = "JSON String contendo metadados adicionais (ex: parâmetros do template, payload do flow).", example = "{\"header\": \"Texto Topo\"}")
    private String metadata;

    @Schema(description = "Lista de opções (botões/respostas) que saem deste passo.")
    private List<BotOptionDTO> options;
}
