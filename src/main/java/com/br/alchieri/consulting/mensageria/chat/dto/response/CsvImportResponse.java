package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado do processamento de um arquivo CSV de contatos.")
public class CsvImportResponse {

    @Schema(description = "Total de linhas lidas do arquivo (excluindo cabeçalho).")
    private int totalRows;

    @Schema(description = "Número de contatos criados com sucesso.")
    private int createdCount;
    
    @Schema(description = "Número de contatos atualizados com sucesso (se a lógica de update for implementada).")
    private int updatedCount;
    
    @Schema(description = "Número de contatos que falharam ao importar.")
    private int failedCount;
    
    @Schema(description = "Lista de erros encontrados durante a importação.")
    private List<String> errors;
}
