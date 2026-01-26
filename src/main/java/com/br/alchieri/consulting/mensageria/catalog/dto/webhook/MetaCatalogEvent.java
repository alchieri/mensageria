package com.br.alchieri.consulting.mensageria.catalog.dto.webhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MetaCatalogEvent {
    private String object; // Esperado: "product_item"
    private List<Entry> entry;

    @Data
    public static class Entry {
        private String id; // ID do Catálogo
        private long time;
        private List<Change> changes;
    }

    @Data
    public static class Change {
        private String field; // ex: "availability", "price", "inventory"
        private Value value;
    }

    @Data
    public static class Value {
        private String id; // ID do Produto na Meta (Facebook ID)
        
        @JsonProperty("retailer_id")
        private String retailerId; // Seu SKU
        
        private String availability; // "in stock", "out of stock"
        private String price;
        private String currency;
    }
}
