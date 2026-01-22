package com.br.alchieri.consulting.mensageria.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta padrão da API para operações bem-sucedidas ou falhas genéricas.")
public class ApiResponse {

    @Schema(description = "Indica se a operação foi bem-sucedida.")
    private boolean success;

    @Schema(description = "Mensagem descritiva sobre o resultado da operação.")
    private String message;

    @Schema(description = "Dados adicionais retornados pela operação (opcional).")
    private Object data; // Opcional, para retornar dados extras (ex: ID da mensagem do WhatsApp)
}
