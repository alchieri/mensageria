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
public class BatchItem {

    @JsonProperty("method")
    private String method; // UPDATE, DELETE

    @JsonProperty("retailer_id")
    private String retailerId; // Seu SKU interno

    @JsonProperty("data")
    private ProductAttributes data;
}
