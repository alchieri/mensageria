package com.br.alchieri.consulting.mensageria.chat.dto.meta;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowPayload {

    @JsonProperty("name")
    private String name; // Namespace (nome técnico) do Flow

    @JsonProperty("parameters")
    private Map<String, Object> parameters; // Parâmetros, incluindo o flow_token
}
