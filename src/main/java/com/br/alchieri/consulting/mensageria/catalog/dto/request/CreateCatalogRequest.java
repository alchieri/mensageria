package com.br.alchieri.consulting.mensageria.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Requisição para criação de um novo Catálogo de Produtos na Meta.")
public class CreateCatalogRequest {

    @NotBlank(message = "O nome do catálogo é obrigatório.")
    @Schema(description = "Nome do catálogo a ser criado.", example = "Catálogo Verão 2026")
    private String name;

    @NotNull(message = "O ID do Business Manager é obrigatório.")
    @Schema(description = "ID do registro local do Meta Business Manager onde o catálogo será criado.", example = "1")
    private Long metaBusinessManagerId;
}
