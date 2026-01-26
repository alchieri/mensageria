package com.br.alchieri.consulting.mensageria.catalog.dto.request;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Requisição para criar um conjunto de produtos (Product Set).")
public class CreateProductSetRequest {

    @NotBlank
    @Schema(description = "Nome do conjunto.", example = "Promoção Verão")
    private String name;

    @Schema(description = "Lista de SKUs (retailer_id) para incluir neste conjunto. Se vazio, cria um conjunto vazio ou usa outro filtro.", example = "[\"SKU-001\", \"SKU-002\"]")
    private List<String> productRetailerIds;
    
    @Schema(description = "Filtro avançado para definir quais produtos incluir no conjunto.", example = "{\"retailer_id\": {\"is_any\": [\"SKU-001\", \"SKU-002\"]}}")
    private Map<String, Object> advancedFilter; 
}
