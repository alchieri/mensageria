package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resultado da operação de envio de templates locais para a Meta API.")
public class TemplatePushResponse {

    @Schema(description = "ID da empresa para a qual a operação foi executada.")
    private Long companyId;
    @Schema(description = "Total de templates encontrados no banco de dados local para esta empresa.")
    private int totalFoundLocally;
    @Schema(description = "Total de templates encontrados na conta da Meta para esta empresa.")
    private int totalFoundInMeta;
    @Schema(description = "Número de templates que existiam localmente e foram submetidos para criação na Meta.")
    private int submittedCount;
    @Schema(description = "Número de templates que já existiam em ambos os locais e foram ignorados.")
    private int alreadySyncedCount;
    @Schema(description = "Lista de nomes dos templates que foram submetidos.")
    private List<String> submittedTemplates;
    @Schema(description = "Lista de erros que ocorreram durante a submissão (se houver).")
    private List<String> errors;
}
