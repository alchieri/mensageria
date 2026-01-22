package com.br.alchieri.consulting.mensageria.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MetaErrorPayload;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Omitir campos nulos na resposta
@Schema(description = "Resposta padronizada para erros da API.")
public class ErrorResponse {

    @Schema(description = "Timestamp da ocorrência do erro.", example = "2025-05-05T10:00:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "Código de status HTTP.", example = "400")
    private int status;

    @Schema(description = "Descrição curta do status HTTP.", example = "Bad Request")
    private String error; // Ex: "Bad Request", "Internal Server Error"

    @Schema(description = "Mensagem detalhada do erro ou lista de erros de validação.")
    private String message; // Mensagem mais detalhada ou lista de erros

    @Schema(description = "Caminho da URI que originou o erro.", example = "/api/v1/messages/text")
    private String path; // O path da requisição que causou o erro

    @Schema(description = "Detalhes de validação (se aplicável).")
    private List<String> validationErrors; // Campo específico para validação

    @Schema(description = "Detalhes adicionais do erro, como erro da API externa (Meta).")
    private MetaErrorPayload externalError; // Campo para o erro estruturado da Meta

    // Construtor para erros gerais
    public ErrorResponse(HttpStatus httpStatus, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = httpStatus.value();
        this.error = httpStatus.getReasonPhrase();
        this.message = message;
        this.path = path;
    }

    // Construtor para erros de validação
    public ErrorResponse(HttpStatus httpStatus, List<String> validationErrors, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = httpStatus.value();
        this.error = httpStatus.getReasonPhrase();
        this.message = "Erro de validação."; // Mensagem genérica para validação
        this.validationErrors = validationErrors;
        this.path = path;
    }

    // Construtor para erros com detalhes externos
    public ErrorResponse(HttpStatus httpStatus, String message, MetaErrorPayload externalError, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = httpStatus.value();
        this.error = httpStatus.getReasonPhrase();
        this.message = message;
        this.externalError = externalError;
        this.path = path;
    }
}
