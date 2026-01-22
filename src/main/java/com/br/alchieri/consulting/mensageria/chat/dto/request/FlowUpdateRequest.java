package com.br.alchieri.consulting.mensageria.chat.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dados para atualizar os metadados de um WhatsApp Flow existente (nome, categorias ou URI do endpoint).")
public class FlowUpdateRequest {

    @Size(min = 1)
    @Schema(description = "Novo nome amigável para o Flow.", example = "Formulário de Suporte v2")
    private String name;

    @Size(min = 1)
    @Schema(description = "Nova lista de categorias para o Flow.", example = "[\"CUSTOMER_SUPPORT\"]")
    private List<String> categories;

    @Schema(description = "A nova URL do Ponto de Extremidade (Endpoint) do Flow.", example = "https://sua-api.com/api/v1/flow-data/receive")
    private String endpointUri;

    @Pattern(regexp = "^\\d+\\.\\d+$", message = "A versão da API de Dados deve estar no formato 'major.minor' (ex: '3.0').")
    @Schema(description = "Nova versão da API de Dados para o Flow.", example = "3.0")
    private String dataApiVersion;
}
