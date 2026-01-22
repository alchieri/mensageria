package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Dados para enviar uma mensagem que inicia um WhatsApp Flow.")
public class SendFlowMessageRequest {

    @NotBlank(message = "O número de destino é obrigatório.")
    @Schema(description = "Número do destinatário no formato E.164.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String to;

    // --- Header (Opcional) ---
    @Schema(description = "Texto do cabeçalho da mensagem do Flow.")
    private String headerText;
    // Adicionar outros tipos de header (imagem, etc.) se necessário

    // --- Body ---
    @NotBlank(message = "O corpo da mensagem é obrigatório.")
    @Schema(description = "Texto principal da mensagem que acompanha o Flow.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bodyText;

    // --- Footer (Opcional) ---
    @Schema(description = "Texto do rodapé da mensagem do Flow.")
    private String footerText;

    // --- Ação do Flow ---
    @NotBlank(message = "O nome (namespace) do Flow é obrigatório.")
    @Schema(description = "Nome (namespace) do seu Flow publicado.", example = "order_food_flow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String flowName;

    @NotBlank
    @Schema(description = "Token único para identificar esta instância específica do Flow (gerado pelo cliente).",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "unique_session_id_12345")
    private String flowToken;

    @Schema(description = "Dados dinâmicos (chave-valor) para a tela inicial do Flow.")
    private Map<String, Object> initialScreenData;

    // @NotBlank(message = "O texto do botão é obrigatório.")
    // @Schema(description = "Texto do botão que o usuário clica para iniciar o Flow.", example = "Fazer Pedido", requiredMode = Schema.RequiredMode.REQUIRED)
    // private String buttonText;

    // Parâmetros a serem passados para a tela inicial do Flow
    @Schema(description = "Parâmetros dinâmicos (chave-valor) para a tela inicial do Flow. As chaves devem corresponder ao que o Flow espera.")
    private Map<String, Object> flowParameters;
}
