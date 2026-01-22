package com.br.alchieri.consulting.mensageria.catalog.dto.meta;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributes {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("availability")
    private String availability; // "in stock", "out of stock"

    @JsonProperty("condition")
    private String condition; // "new", "refurbished", "used"

    @JsonProperty("price")
    private Long priceAmount; // Valor * 100 (ex: 1000 para 10.00) ou formato centavos

    @JsonProperty("currency")
    private String currency; // "BRL", "USD"

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("url")
    private String url; // Link para o produto no site (obrigatório pelo FB)

    @JsonProperty("image_url")
    private String imageUrl; // Link público da imagem
    
    @JsonProperty("category")
    private String category; // Categoria Google Product Category ID (opcional mas recomendado)
}
