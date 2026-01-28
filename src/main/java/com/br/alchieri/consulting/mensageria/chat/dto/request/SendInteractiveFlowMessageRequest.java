package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para enviar uma mensagem interativa que inicia um Flow (dentro da janela de 24h).")
public class SendInteractiveFlowMessageRequest {

    @NotBlank(message = "O número de destino é obrigatório.")
    @Schema(description = "Número do destinatário no formato E.164.", requiredMode = Schema.RequiredMode.REQUIRED, example = "5511999998888")
    private String to;

    @Schema(description = "Texto para o cabeçalho da mensagem (opcional).")
    private String headerText;

    @NotBlank(message = "O corpo da mensagem é obrigatório.")
    @Schema(description = "Texto principal da mensagem que introduz o Flow.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Para continuar, por favor, preencha nosso formulário.")
    private String bodyText;

    @NotBlank(message = "O texto do rodapé é obrigatório.")
    @Schema(description = "Texto do rodapé da mensagem.", requiredMode = Schema.RequiredMode.REQUIRED, example = "Toque no botão para começar.")
    private String footerText;

    @NotBlank(message = "O nome (namespace) do Flow é obrigatório.")
    @Schema(description = "Nome técnico (namespace) do seu Flow publicado.", example = "agendamento_v1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String flowName; // ou flowId

    private String flowId;

    @NotBlank(message = "O texto do botão (CTA) é obrigatório.")
    @Size(max = 20, message = "O texto do botão CTA não pode exceder 20 caracteres.")
    @Schema(description = "Texto do botão que o usuário clica para abrir o Flow.", example = "Preencher Formulário", requiredMode = Schema.RequiredMode.REQUIRED)
    private String flowCta;

    @Schema(description = "Token único para esta sessão específica do Flow. Gerado pelo seu sistema para rastreamento. Se não fornecido, um valor padrão ('unused') é usado pela Meta.")
    private String flowToken;

    @Schema(description = "Modo do Flow ('draft' ou 'published'). Útil para testar versões não publicadas.", defaultValue = "published", allowableValues = {"draft", "published"})
    private String mode = "published";

    @Schema(description = "Ação a ser executada ao abrir o Flow ('navigate' ou 'data_exchange').", defaultValue = "navigate", allowableValues = {"navigate", "data_exchange"})
    private String flowAction = "navigate";

    @Valid
    @Schema(description = "Payload para a ação do Flow. Obrigatório se 'flowAction' for 'navigate' ou 'data_exchange'. Contém 'screen' e 'data' para a primeira tela.")
    private FlowActionPayload flowActionPayload;

    @Schema(description = "ID do telefone (Meta ID) que enviará a mensagem. Se nulo, usa o padrão da empresa.", example = "10555...")
    private String fromPhoneNumberId;

    @Data
    @Schema(name = "InteractiveFlowActionPayload")
    public static class FlowActionPayload {
        
        @NotBlank(message = "O nome da tela de destino é obrigatório para a ação 'navigate'.")
        @JsonProperty("screen") // Garante o nome correto no JSON
        @Schema(description = "ID da tela para a qual o Flow deve navegar ao ser aberto.", requiredMode = Schema.RequiredMode.REQUIRED, example = "WELCOME_SCREEN")
        private String screen;
        
        @JsonProperty("data") // Garante o nome correto no JSON
        @Schema(description = "Objeto JSON com dados de entrada para pré-preencher a primeira tela (opcional).")
        private Map<String, Object> data;
    }
}
