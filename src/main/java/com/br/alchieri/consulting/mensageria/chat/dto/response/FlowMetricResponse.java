package com.br.alchieri.consulting.mensageria.chat.dto.response;

import java.util.List;
import java.util.Map;

import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowMetricName;
import com.br.alchieri.consulting.mensageria.chat.model.enums.MetricGranularity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Resposta da API de Métricas para um WhatsApp Flow.")
public class FlowMetricResponse {

    @Schema(description = "ID do Flow na plataforma da Meta.")
    private String id;

    @JsonProperty("metric")
    private MetricData metric;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetricData {
        private MetricGranularity granularity;
        private FlowMetricName name;

        @JsonProperty("data_points")
        private List<DataPoint> dataPoints;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataPoint {
        private String timestamp; // Mantém como String no formato ISO 8601
        private List<Map<String, Object>> data;
    }
}
