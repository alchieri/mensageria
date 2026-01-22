package com.br.alchieri.consulting.mensageria.chat.dto.meta;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * Representa o payload para criar um novo Message Template via Business Management API.
 * Referência: https://developers.facebook.com/docs/whatsapp/business-management-api/message-templates#create-message-templates
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WhatsAppBusinessApiCreateTemplateRequest {

    @JsonProperty("name")
    private String name; // Nome do template (minúsculas, underscores)

    @JsonProperty("language")
    private String language; // Código do idioma (ex: "pt_BR")

    @JsonProperty("category")
    private String category; // "MARKETING", "UTILITY", "AUTHENTICATION"

    @JsonProperty("allow_category_change")
    @Builder.Default
    private boolean allowCategoryChange = false;

    @JsonProperty("components")
    @Singular // Permite adicionar componentes um por um
    private List<ComponentDefinition> components;

    // --- Classes Internas Aninhadas ---

    /**
     * Define um componente (HEADER, BODY, FOOTER, BUTTONS) do template.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentDefinition {
        @JsonProperty("type")
        private String type; // "HEADER", "BODY", "FOOTER", "BUTTONS"

        // Apenas para HEADER
        @JsonProperty("format")
        private String format; // "TEXT", "IMAGE", "VIDEO", "DOCUMENT", "LOCATION" (LOCATION não usual em template)

        // Para HEADER (format=TEXT), BODY, FOOTER
        @JsonProperty("text")
        private String text; // Texto do componente, pode conter variáveis {{1}}, {{2}}

        // Opcional: Exemplos para preencher variáveis na UI da Meta
        @JsonProperty("example")
        private ExampleDefinition example;

        // Apenas para type=BUTTONS
        @JsonProperty("buttons")
        @Singular // Permite adicionar botões um por um
        @JsonInclude(JsonInclude.Include.NON_EMPTY) // Inclui só se não estiver vazio
        private List<ButtonDefinition> buttons;
    }

    /**
     * Define exemplos de valores para as variáveis do template.
     * Ajuda na revisão pela Meta.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ExampleDefinition {
        // Exemplo para HEADER tipo TEXT com uma variável {{1}}
        @JsonProperty("header_text")
        @Singular("addHeaderText")
        private List<String> headerText; // Ex: ["Example Customer Name"]

        // Exemplo para HEADER tipo IMAGE/VIDEO/DOCUMENT (ID da mídia exemplo)
        @JsonProperty("header_handle")
        @Singular("addHeaderHandle")
        private List<String> headerHandle; // Ex: ["media-id-example-123"]

        // Exemplo para BODY com variáveis {{1}}, {{2}}
        @JsonProperty("body_text")
        @Singular("addBodyText")
         // Lista de listas, onde cada lista interna corresponde a um conjunto de variáveis para o corpo
        private List<List<String>> bodyText; // Ex: [["ValueForVar1", "ValueForVar2"], ["AnotherVal1", "AnotherVal2"]]

         // Exemplo para URL em botões com variável
         // A API espera um Map<String, List<String>>, mas Jackson pode ter dificuldade
         // Verificar docs, pode ser mais simples incluir no example do botão diretamente
         //@JsonProperty("url_placeholders")
         //private Map<String, List<String>> urlPlaceholders;
    }

    /**
     * Define um botão dentro do componente BUTTONS.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ButtonDefinition {
        @JsonProperty("type")
        private String type; // "QUICK_REPLY", "URL", "PHONE_NUMBER", "COPY_CODE", "CATALOG" (verificar tipos atuais)

        // Para type="OTP"
        @JsonProperty("otp_type") 
        private String otpType;

        // Para type="OTP" e otp_type="ONE_TAP_AUTOFIL" (Android)
        @JsonProperty("package_name_android")
        private String packageNameAndroid;

        // Para type="OTP" e otp_type="ONE_TAP_AUTOFIL" (iOS)
        @JsonProperty("signature_hash_ios")
        private String signatureHashIos;

        @JsonProperty("text")
        private String text; // Texto exibido no botão

        // Apenas para type=URL
        @JsonProperty("url")
        private String url; // URL estática ou com UMA variável (ex: https://site.com/{{1}})

        // Apenas para type=PHONE_NUMBER
        @JsonProperty("phone_number")
        private String phoneNumber; // Número de telefone com código do país

        // Apenas para type=COPY_CODE
        @JsonProperty("coupon_code") // Nome pode variar, verificar docs
        private String couponCode; // Código exemplo

        // Exemplo para variável na URL (se houver)
        // Não é `payload` como no envio, é parte da definição
        @JsonProperty("example")
        @Singular("addExample")
        private List<String> example; // Ex: ["value-for-url-var"] (para URL https://site.com/{{1}})

        @JsonProperty("flow_name")
        private String flowName;

        @JsonProperty("flow_id")
        private String flowId;
    }
}
