package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado da operação de sincronização de templates da Meta API.")
public class TemplateSyncResponse {

    @Schema(description = "Total de templates encontrados na conta da Meta.")
    private int totalFoundInMeta;

    @Schema(description = "Número de novos templates importados para o sistema.")
    private int importedCount;
    
    @Schema(description = "Número de templates locais que foram atualizados com base nos dados da Meta.")
    private int updatedCount;
    
    @Schema(description = "Número de templates que já estavam sincronizados e não precisaram de atualização.")
    private int alreadySyncedCount;
    
    @Schema(description = "Lista de nomes dos templates que foram importados/atualizados.")
    private List<String> processedTemplates;
}
