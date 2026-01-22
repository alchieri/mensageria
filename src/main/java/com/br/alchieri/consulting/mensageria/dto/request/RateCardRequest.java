package com.br.alchieri.consulting.mensageria.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Dados para criar ou atualizar uma entrada na tabela de tarifas da Meta.")
public class RateCardRequest {

    @NotBlank(message = "O nome do mercado/região é obrigatório.")
    @Schema(example = "Brazil", requiredMode = Schema.RequiredMode.REQUIRED)
    private String marketName;

    @Schema(description = "Código do país (ex: 55). Pode ser nulo para regiões.", example = "55")
    private String countryCode;

    @NotBlank(message = "A moeda é obrigatória.")
    @Schema(example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;

    @NotNull(message = "A categoria é obrigatória.")
    private TemplateCategory category;

    @NotNull(message = "O início da faixa de volume é obrigatório.")
    @Min(0)
    @Schema(description = "Início da faixa de volume (ex: 0 para o primeiro tier, 250001 para o segundo).", example = "250001")
    private Long volumeTierStart;

    @Min(0)
    @Schema(description = "Fim da faixa de volume (nulo para a última faixa 'infinita').", example = "750000")
    private Long volumeTierEnd;

    @NotNull(message = "A tarifa é obrigatória.")
    @DecimalMin(value = "0.0", inclusive = false, message = "A tarifa deve ser maior que zero.")
    @Schema(description = "Valor da tarifa para esta faixa.", example = "0.0065", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal rate;

    @NotNull(message = "A data de vigência é obrigatória.")
    @Schema(description = "Data a partir da qual esta tarifa é válida (formato YYYY-MM-DD).", example = "2026-01-01")
    private LocalDate effectiveDate;
}
