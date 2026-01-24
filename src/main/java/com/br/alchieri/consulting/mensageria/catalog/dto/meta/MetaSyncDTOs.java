package com.br.alchieri.consulting.mensageria.catalog.dto.meta;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public class MetaSyncDTOs {

    // --- RESPOSTA DE CATÁLOGOS ---
    @Data
    public static class MetaCatalogListResponse {
        private List<MetaCatalogData> data;
        private MetaPaging paging;
    }

    @Data
    public static class MetaCatalogData {
        private String id; // ID do Catálogo na Meta
        private String name;
    }

    // --- RESPOSTA DE PRODUTOS ---
    @Data
    public static class MetaProductListResponse {
        private List<MetaProductData> data;
        private MetaPaging paging;
    }

    @Data
    public static class MetaProductData {
        private String id; // ID do Produto na Meta (FB ID)
        
        @JsonProperty("retailer_id")
        private String retailerId; // SKU / ID externo
        
        private String name;
        private String description;
        
        @JsonProperty("image_url")
        private String imageUrl;
        
        private String availability;
        private String price;     // Vem como "100 BRL" ou numérico dependendo da API
        private String currency; 
    }

    @Data
    public static class MetaPaging {
        private MetaCursors cursors;
        private String next;
    }

    @Data
    public static class MetaCursors {
        private String before;
        private String after;
    }
}
