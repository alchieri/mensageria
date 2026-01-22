package com.br.alchieri.consulting.mensageria.chat.dto.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * Representa o payload base para enviar mensagens via WhatsApp Cloud API.
 * Use o padrão Builder para construir a requisição, definindo APENAS o campo de payload
 * correspondente ao 'type' (ex: text, template, interactive, etc.).
 */
@Data
@Builder // Facilita a construção, especialmente com muitos campos opcionais
@JsonInclude(JsonInclude.Include.NON_NULL) // Omitir campos não definidos no JSON final
public class WhatsAppCloudApiRequest {

    @JsonProperty("messaging_product")
    @Builder.Default // Garante valor padrão mesmo com builder
    private String messagingProduct = "whatsapp";

    @JsonProperty("recipient_type")
    @Builder.Default
    private String recipientType = "individual";

    @JsonProperty("to")
    private String to; // Número do destinatário

    @JsonProperty("type")
    private String type; // "text", "template", "interactive", "image", etc.

    // --- Campos específicos do tipo de mensagem (APENAS UM deve ser não nulo) ---

    @JsonProperty("text")
    private TextPayload text;

    @JsonProperty("template")
    private WhatsAppTemplatePayload template;

    @JsonProperty("interactive")
    private InteractivePayload interactive; // Para botões e listas

    @JsonProperty("image")
    private MediaPayload image;

    @JsonProperty("audio")
    private MediaPayload audio;

    @JsonProperty("document")
    private MediaPayload document;

    @JsonProperty("video")
    private MediaPayload video;

    @JsonProperty("sticker")
    private MediaPayload sticker;

    @JsonProperty("location")
    private LocationPayload location;

    // Adicionar outros tipos conforme necessário (contacts, etc.)

    // --- Payloads Internos ---

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextPayload {
        @JsonProperty("preview_url")
        @Builder.Default
        private boolean previewUrl = false;

        @JsonProperty("body")
        private String body;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaPayload {
        @JsonProperty("id")
        private String id; // ID da mídia previamente carregada

        @JsonProperty("link")
        private String link; // URL pública da mídia (menos comum para envio via ID)

        @JsonProperty("caption")
        private String caption; // Legenda (para image, video, document)

        @JsonProperty("filename")
        private String filename; // Nome do arquivo (principalmente para document)
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LocationPayload {
        @JsonProperty("latitude")
        private double latitude;
        @JsonProperty("longitude")
        private double longitude;
        @JsonProperty("name")
        private String name;
        @JsonProperty("address")
        private String address;
    }

    // InteractivePayload e TemplatePayload são mais complexos e definidos separadamente abaixo.
}
