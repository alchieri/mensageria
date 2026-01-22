package com.br.alchieri.consulting.mensageria.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductSyncRequest {
    
    @NotBlank(message = "O SKU (retailer_id) é obrigatório.")
    private String sku;

    @NotBlank(message = "O nome do produto é obrigatório.")
    private String name;

    private String description;

    @NotBlank(message = "A URL da imagem é obrigatória.")
    private String imageUrl;

    @NotBlank(message = "A URL do produto no site é obrigatória.")
    private String websiteUrl;

    @NotNull(message = "O preço é obrigatório.")
    @Positive
    private Double price; // Valor decimal (ex: 10.50)

    private String currency = "BRL";
    private String brand = "Generic";
    private boolean inStock = true;
}
