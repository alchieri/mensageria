package com.br.alchieri.consulting.mensageria.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.model.MetaRateCard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Representação de uma entrada na tabela de tarifas da Meta.")
public class MetaRateCardResponse {
    private Long id;
    private String marketName;
    private String countryCode;
    private String currency;
    private TemplateCategory category;
    private Long volumeTierStart;
    private Long volumeTierEnd;
    private BigDecimal rate;
    private LocalDate effectiveDate;

    public static MetaRateCardResponse fromEntity(MetaRateCard entity) {
        if (entity == null) return null;
        return MetaRateCardResponse.builder()
                .id(entity.getId())
                .marketName(entity.getMarketName())
                .countryCode(entity.getCountryCode())
                .currency(entity.getCurrency())
                .category(entity.getCategory())
                .volumeTierStart(entity.getVolumeTierStart())
                .volumeTierEnd(entity.getVolumeTierEnd())
                .rate(entity.getRate())
                .effectiveDate(entity.getEffectiveDate())
                .build();
    }
}
