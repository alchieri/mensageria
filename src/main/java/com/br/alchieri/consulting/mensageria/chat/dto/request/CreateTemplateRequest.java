package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "Dados para criar um novo modelo de mensagem do WhatsApp.")
public class CreateTemplateRequest {

    @NotBlank
    @Schema(description = "Nome do template (minúsculas, underscores, sem espaços). Deve ser único para o idioma dentro da conta.", example = "boas_vindas_cliente_v1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name; // Nome do template (minúsculas, underscores)

    @NotBlank
    @Schema(description = "Categoria do template.", allowableValues = {"AUTHENTICATION", "MARKETING", "UTILITY"}, example = "UTILITY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String category; // MARKETING, UTILITY, AUTHENTICATION

    @NotBlank
    @Schema(description = "Código do idioma (ex: pt_BR, en_US).", example = "pt_BR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String language; // ex: pt_BR
    
    @NotEmpty(message = "Pelo menos um componente é necessário.")
    @Valid
    @Schema(description = "Lista de componentes que formam o template (HEADER, BODY, FOOTER, BUTTONS).", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TemplateComponentDefinition> components; // Header, Body, Footer, Buttons

    @JsonProperty("allow_category_change")
    @Schema(description = "Permite que a categoria do template seja alterada pela Meta durante a revisão, se necessário.", defaultValue = "false")
    private boolean allowCategoryChange = false; // Opcional

    @Data
    @Schema(description = "Define um componente de um modelo de mensagem (HEADER, BODY, FOOTER, BUTTONS).")
    public static class TemplateComponentDefinition {

        @NotBlank
        @Schema(description = "Tipo do componente.", allowableValues = {"HEADER", "BODY", "FOOTER", "BUTTONS"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String type; // HEADER, BODY, FOOTER, BUTTONS

        @Schema(description = "Formato do componente (relevante para HEADER).", allowableValues = {"TEXT", "IMAGE", "VIDEO", "DOCUMENT"})
        private String format; // Para HEADER: TEXT, IMAGE, VIDEO, DOCUMENT, LOCATION

        @Schema(description = "Texto do componente. Pode conter variáveis como {{1}}, {{2}}.", example = "Olá {{1}}, seu pedido nº {{2}} foi confirmado.")
        private String text; // Para HEADER (text), BODY, FOOTER

        @Valid
        @Schema(description = "Exemplos de valores para as variáveis (opcional, mas ajuda na aprovação).")
        private TemplateExampleDefinition example; // Opcional, para exemplos na UI da Meta

        @Valid
        @Schema(description = "Lista de botões (apenas para type=BUTTONS).")
        private List<ButtonDefinition> buttons; // Para type=BUTTONS
    }

    @Data
    @Schema(description = "Define um exemplo de um modelo de mensagem.")
    public static class TemplateExampleDefinition {
        // Ex: header_text: ["Example Header Text"], body_text: [["value1", "value2"]]
        @JsonProperty("header_text")
        @Schema(description = "Lista de header text. Ex: header_text: [\"Example Header Text\"]")
        private List<String> headerText;

        @JsonProperty("header_handle")
        @Schema(description = "Lista de handler para header de imagem/video/doc ID")
        private List<String> headerHandle; // Para header de imagem/video/doc ID

        @JsonProperty("body_text")
        @Schema(description = "Lista de body text. Ex: body_text: [[\"value1\", \"value2\"]]")
        private List<List<String>> bodyText;
        // ... outros exemplos
    }

    @Data
    @Schema(description = "Define um botão de um modelo de mensagem.")
    public static class ButtonDefinition {

        @NotBlank
        @Schema(description = "Tipo do botão.", allowableValues = {"OTP", "QUICK_REPLY", "URL", "PHONE_NUMBER", "FLOW "}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String type; // QUICK_REPLY, URL, PHONE_NUMBER, FLOW 

        @NotBlank
        @Schema(description = "Texto do botão. Pode conter variáveis como {{1}}, {{2}}.", example = "Olá {{1}}, seu pedido nº {{2}} foi confirmado.")
        private String text; // Texto do botão

        // Apenas para URL
        @Schema(description = "Define uma URL para um botão quando link.")
        private String url;

        @Schema(description = "Exemplos de URL para as variáveis (opcional, mas ajuda na aprovação).")
        private List<String> example; // Exemplo de variável na URL

        // Apenas para PHONE_NUMBER
        @JsonProperty("phone_number") // Mapeia para JSON snake_case
        @Schema(description = "Define um phone number quando o tipo é PHONE_NUMBER e mapeia para JSON snake_case.")
        private String phoneNumber; // Usa camelCase no Java

        // Para OTP
        @JsonProperty("otp_type")
        private String otpType; // Ex: "COPY_CODE", "ONE_TAP_AUTOFIL" (se aplicável na definição)

        // Para ONE_TAP_AUTOFIL
        @JsonProperty("package_name_android")
        private String packageNameAndroid;

        @JsonProperty("signature_hash_ios")
        private String signatureHashIos;

        // --- Campos para type=FLOW ---
        @Schema(description = "Nome técnico (namespace) do Flow a ser associado a este botão.")
        private String flowName;
        
        @Schema(description = "ID do Flow na Meta a ser associado a este botão.")
        private String flowId;

        // Apenas para COPY_CODE
        // @JsonProperty("coupon_code")
        // @Schema(description = "Define um coupon code.")
        // private String couponCode; // Nome correto do campo JSON na API Meta

        // Campo 'payload' NÃO existe na DEFINIÇÃO de quick_reply, apenas no ENVIO
    }
}
