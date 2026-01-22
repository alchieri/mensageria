package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado da operação de sincronização de Flows da Meta API.")
public class FlowSyncResponse {
    @Schema(description = "Total de Flows encontrados na conta da Meta.")
    private int totalFoundInMeta;
    @Schema(description = "Número de novos Flows importados para o sistema.")
    private int importedCount;
    @Schema(description = "Número de Flows locais que foram atualizados com base nos dados da Meta.")
    private int updatedCount;
    @Schema(description = "Número de Flows que já estavam sincronizados.")
    private int alreadySyncedCount;
    @Schema(description = "Lista de nomes dos Flows que foram importados/atualizados.")
    private List<String> processedFlows;
}
