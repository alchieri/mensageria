package com.br.alchieri.consulting.mensageria.chat.dto.meta;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * Representa o payload 'template' dentro da requisição da Cloud API.
 * Usado quando WhatsAppCloudApiRequest.type = "template".
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WhatsAppTemplatePayload {

    @JsonProperty("name")
    private String name; // Nome do template

    @JsonProperty("language")
    private Language language;

    @JsonProperty("components")
    @Singular // Permite adicionar componentes um por um: .component(c1).component(c2)
    private List<Component> components;

    // --- Classes Internas ---

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Language {
        @JsonProperty("code")
        private String code; // Ex: "pt_BR"
    }

    /**
     * Representa um componente (HEADER, BODY, BUTTONS) a ser preenchido no template.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Component {
        @JsonProperty("type")
        private String type; // "header", "body", "button" (singular no envio)

        // Apenas para type="button"
        @JsonProperty("sub_type")
        private String subType; // "quick_reply", "url"

        // Apenas para type="button"
        @JsonProperty("index")
        private String index; // Índice do botão (como string: "0", "1", ...)

        @JsonProperty("parameters")
        @Singular // Permite adicionar parâmetros um por um: .parameter(p1).parameter(p2)
        private List<Parameter> parameters;
    }

    /**
     * Representa um parâmetro a ser substituído dentro de um componente.
     * APENAS UM dos campos de valor (text, currency, image, etc.) deve ser preenchido.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameter {
        @JsonProperty("type")
        private String type; // "text", "currency", "date_time", "image", "document", "video", "payload" (para quick_reply)

        // --- Campos de Valor (APENAS UM deve ser não nulo) ---
        @JsonProperty("text")
        private String text;

        @JsonProperty("currency")
        private Currency currency;

        @JsonProperty("date_time")
        private DateTime dateTime;

        @JsonProperty("image")
        private MediaObject image;

        @JsonProperty("document")
        private MediaObject document;

        @JsonProperty("video")
        private MediaObject video;

        // Apenas para botões quick_reply (sub_type="quick_reply")
        @JsonProperty("payload")
        private String payload;

        private Object action;
    }

    // --- Tipos de Parâmetros Complexos ---

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Currency {
        @JsonProperty("fallback_value")
        private String fallbackValue; // Valor a ser exibido se a formatação falhar
        @JsonProperty("code")
        private String code; // Código ISO 4217 (ex: "BRL")
        @JsonProperty("amount_1000")
        private long amount1000; // Valor multiplicado por 1000 (ex: R$ 12,34 -> 12340)
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateTime {
        @JsonProperty("fallback_value")
        private String fallbackValue; // Valor textual a ser exibido
        // A API suporta timestamp, mas o fallback_value é geralmente suficiente
        // Ver documentação para outros campos como 'day_of_week', 'year', 'month', etc.
         @JsonProperty("timestamp") // Exemplo usando timestamp epoch seconds
         private Long timestamp; // Segundos desde a epoch UTC
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaObject { // Usado para image, document, video
        @JsonProperty("id")
        private String id; // ID da mídia carregada

        @JsonProperty("link")
        private String link; // URL pública (usar um ou outro)

        // Apenas para document
        @JsonProperty("filename")
        private String filename;
    }
}
