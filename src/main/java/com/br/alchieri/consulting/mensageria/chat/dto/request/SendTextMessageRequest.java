package com.br.alchieri.consulting.mensageria.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados necessários para enviar uma mensagem de texto simples.") // Descrição do DTO
public class SendTextMessageRequest {

    @NotBlank(message = "O número de destino é obrigatório.")
    @Size(min = 10, max = 15, message = "Número de destino inválido (formato com código do país, ex: 55119...).") // Ajuste o size
    @Schema(description = "Número do destinatário no formato E.164 (código do país + DDD + número, sem '+').",
            example = "5511999998888", requiredMode = Schema.RequiredMode.REQUIRED) // Descrição e exemplo do campo
    private String to;

    @NotBlank(message = "A mensagem não pode estar vazia.")
    @Size(max = 4096, message = "Mensagem muito longa.") // Limite do WhatsApp
    @Schema(description = "Conteúdo da mensagem de texto.", maxLength = 4096,
            example = "Olá! Este é um teste.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
