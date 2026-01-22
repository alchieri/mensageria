package com.br.alchieri.consulting.mensageria.catalog.dto.meta;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaProductBatchRequest {

    @JsonProperty("requests")
    private List<BatchItem> requests;
}
