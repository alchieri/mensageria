package com.br.alchieri.consulting.mensageria.chat.dto.meta;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

/**
 * Representa o payload 'interactive' dentro da requisição da Cloud API.
 * Usado quando WhatsAppCloudApiRequest.type = "interactive".
 * Referência: https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages#interactive-object
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InteractivePayload {

    @JsonProperty("type")
    private String type; // "button", "list", "product", "product_list", "catalog_message", "flow" (Verificar tipos atuais)

    @JsonProperty("header")
    private Header header; // Opcional

    @JsonProperty("body")
    private Body body; // Obrigatório

    @JsonProperty("footer")
    private Footer footer; // Opcional

    @JsonProperty("action")
    private Action action; // Obrigatório (contém botões, seções, etc.)

    // --- Classes Internas Aninhadas ---

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Header {
        @JsonProperty("type")
        private String type; // "text", "video", "image", "document"

        // Apenas um destes deve ser preenchido
        @JsonProperty("text")
        private String text; // Max 60 chars

        @JsonProperty("video")
        private WhatsAppTemplatePayload.MediaObject video; // Reutiliza MediaObject do WhatsAppTemplatePayload

        @JsonProperty("image")
        private WhatsAppTemplatePayload.MediaObject image;

        @JsonProperty("document")
        private WhatsAppTemplatePayload.MediaObject document;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Body {
        @JsonProperty("text")
        private String text; // Obrigatório, Max 1024 chars
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Footer {
        @JsonProperty("text")
        private String text; // Opcional, Max 60 chars
    }

    /**
     * Contém a definição dos elementos interativos (botões, seções de lista, etc.)
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        // Apenas para interactive type="button"
        @JsonProperty("buttons")
        @Singular
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<Button> buttons; // Max 3 botões

        // Apenas para interactive type="list" ou "product_list"
        @JsonProperty("button") // Texto do botão que abre a lista
        private String button; // Max 20 chars

        @JsonProperty("sections")
        @Singular
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<Section> sections; // Max 10 seções

        // Apenas para interactive type="catalog_message"
        @JsonProperty("catalog_id")
        private String catalogId;

        // Apenas para interactive type="catalog_message"
        @JsonProperty("thumbnail_product_retailer_id")
        private String thumbnailProductRetailerId; // Opcional

        // Apenas para interactive type="flow"
        @JsonProperty("name")
        private String flowName; // Nome do Flow

        @JsonProperty("parameters") // Para Flow
        private Map<String, Object> flowParameters; // Estrutura depende do Flow

        @JsonProperty("product_retailer_id")
        private String productRetailerId;

        // Outros campos para product/product_list (catalog_id) podem ser necessários
    }

    /**
     * Representa um botão de resposta rápida (reply button).
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Button {
        @JsonProperty("type")
        @Builder.Default
        private String type = "reply"; // Único tipo suportado atualmente

        @JsonProperty("reply")
        private Reply reply;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Reply {
        @JsonProperty("id")
        private String id; // ID único para o botão (Max 256 chars)
        @JsonProperty("title")
        private String title; // Texto do botão (Max 20 chars)
    }

    /**
     * Representa uma seção em uma mensagem do tipo lista.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Section {
        @JsonProperty("title")
        private String title; // Opcional para listas com 1 seção, obrigatório se > 1. Max 24 chars.

        @JsonProperty("rows")
        @Singular
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<Row> rows; // Max 10 linhas por seção

        // Apenas para interactive type="product_list"
        @JsonProperty("product_items")
        @Singular("addProductItem")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ProductItem> productItems;
    }

    /**
     * Representa uma linha em uma seção de lista.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Row {
        @JsonProperty("id")
        private String id; // ID único para a linha (Max 200 chars)
        @JsonProperty("title")
        private String title; // Texto principal da linha (Max 24 chars)
        @JsonProperty("description")
        private String description; // Opcional, texto secundário (Max 72 chars)
    }

     /**
     * Representa um item de produto em uma lista de produtos.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductItem {
        @JsonProperty("product_retailer_id")
        private String productRetailerId;
    }
}
