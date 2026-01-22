package com.br.alchieri.consulting.mensageria.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Representa um parâmetro individual para um componente de template.")
public class TemplateParameterRequest {

    @NotBlank(message = "O tipo do parâmetro é obrigatório (text, currency, date_time, image, document, video).")
    @Schema(description = "Tipo do parâmetro.",
            allowableValues = {"text", "currency", "date_time", "image", "document", "video", "payload"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type; // "text", "currency", "date_time", "image", "document", "video"

    // --- Preencher APENAS o campo correspondente ao 'type' ---
    @Schema(description = "Valor para parâmetro do tipo 'text'.", example = "Cliente Teste")
    private String text;

    // Para type="image", "document", "video"
    @Schema(description = "ID da mídia (para tipo 'image', 'document', 'video') obtido via upload prévio.")
    private String mediaId; // ID obtido via upload prévio

    // Para type="document" (opcional, se quiser sobrescrever o nome original)
    @Schema(description = "Nome do arquivo (opcional para tipo 'document').", example = "contrato.pdf")
    private String filename;

    // Para type="payload" (botões quick_reply)
    @Schema(description = "Payload para parâmetro do tipo 'payload' (usado em botões de resposta rápida).", example = "USER_CONFIRMED_YES")
    private String payload;

    // Exemplo para Currency (requer DTO próprio CurrencyRequest)
    @Valid
    @Schema(description = "Dados para parâmetro do tipo 'currency'.")
    private CurrencyRequest currency;

    // Exemplo para DateTime (requer DTO próprio DateTimeRequest)
    @Valid
    @Schema(description = "Dados para parâmetro do tipo 'date_time'.")
    private DateTimeRequest dateTime;

    private Object action;

    // --- DTOs Internos para Tipos Complexos ---
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Detalhes para um parâmetro do tipo 'currency'.")
    public static class CurrencyRequest {
        
        @NotBlank
        @Schema(description = "Valor textual de fallback.", example = "R$10,99", requiredMode = Schema.RequiredMode.REQUIRED)
        private String fallbackValue;

        @NotBlank
        @Schema(description = "Código ISO 4217 da moeda.", example = "BRL", requiredMode = Schema.RequiredMode.REQUIRED)
        private String code; // "BRL"

        @NotNull
        @Schema(description = "Valor multiplicado por 1000 (ex: 12340 para 12.34).", example = "12340", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long amount1000; // 12340 para R$ 12,34
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Detalhes para um parâmetro do tipo 'date_time'.")
    public static class DateTimeRequest {
        
        @NotBlank
        @Schema(description = "Valor textual de fallback para a data/hora.", example = "05 de Maio de 2025", requiredMode = Schema.RequiredMode.REQUIRED)
        private String fallbackValue; // Formato livre
        // Opcional: Enviar timestamp em segundos UTC se a API do seu cliente fornecer
        // private Long timestamp;
    }
}
