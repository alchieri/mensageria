package com.br.alchieri.consulting.mensageria.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Representa uma opção de resposta ou botão em um passo.")
public class BotOptionDTO {
    
    @Schema(description = "ID da opção.")
    private Long id;

    @Schema(description = "Palavra-chave ou payload do botão para match.", example = "1")
    private String keyword;

    @Schema(description = "Texto visível da opção/botão.", example = "Falar com Financeiro")
    private String label;

    @Schema(description = "Ordem de exibição.", example = "1")
    private Integer sequence;

    @Schema(description = "Se verdadeiro, transfere para um humano ao ser clicado.")
    private boolean isHandoff;

    @Schema(description = "ID do passo para onde o usuário será enviado.")
    private Long targetStepId; 
}
