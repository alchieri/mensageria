package com.br.alchieri.consulting.mensageria.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.br.alchieri.consulting.mensageria.chat.dto.response.MetaErrorPayload;
import com.br.alchieri.consulting.mensageria.dto.response.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@ControllerAdvice // Indica que esta classe vai tratar exceções globalmente
@RequiredArgsConstructor // Injeta o ObjectMapper final
public class GlobalExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
        private final ObjectMapper objectMapper; // Injetado

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationExceptions(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {

                List<String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(Collectors.toList());

                logger.warn("Erro de validação na requisição {}: {}", request.getRequestURI(), errors);

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST,
                                errors,
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        // Handler para erros do WebClient (ex: 4xx/5xx da API Meta)
        @ExceptionHandler(WebClientResponseException.class)
        public ResponseEntity<ErrorResponse> handleWebClientResponseException(
                        WebClientResponseException ex, HttpServletRequest request) {

                HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
                if (status == null) {
                        status = HttpStatus.INTERNAL_SERVER_ERROR; // Fallback
                        logger.warn("WebClientResponseException com status code não mapeável: {}", ex.getStatusCode());
                }

                String responseBody = ex.getResponseBodyAsString();
                String primaryMessage = "Falha na comunicação com o serviço externo."; // Default
                MetaErrorPayload metaError = null;

                logger.error("WebClientResponseException em {}: Status={}, Body={}", request.getRequestURI(), ex.getStatusCode(), responseBody, ex);

                try {
                        if (responseBody != null && !responseBody.isBlank()) {
                                JsonNode errorRootNode = objectMapper.readTree(responseBody);
                                if (errorRootNode.has("error")) {
                                        JsonNode metaErrorNode = errorRootNode.get("error");
                                        metaError = objectMapper.treeToValue(metaErrorNode, MetaErrorPayload.class);
                                        primaryMessage = metaError.getMessage() != null ? metaError.getMessage() : primaryMessage;
                                        logger.warn("Erro detalhado da Meta API: Code={}, Subcode={}, Type={}, Message={}",
                                                metaError.getCode(), metaError.getErrorSubcode(), metaError.getType(), metaError.getMessage());
                                } else {
                                        primaryMessage = responseBody.length() < 250 ? responseBody : primaryMessage; // Usa corpo se for curto e não JSON de erro Meta
                                }
                        }
                } catch (JsonProcessingException jpe) {
                        logger.warn("Não foi possível fazer parse do corpo da resposta de erro da API externa como JSON: {}", responseBody);
                        primaryMessage = "Recebida resposta de erro malformada do serviço externo.";
                } catch (Exception e) { // Outras exceções ao processar o corpo do erro
                        logger.error("Erro inesperado ao processar corpo de erro da WebClientResponseException:", e);
                        primaryMessage = "Erro interno ao processar falha do serviço externo.";
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        status,
                        primaryMessage,
                        metaError, // Passa o objeto de erro da Meta (pode ser nulo)
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, status);
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
                        NoResourceFoundException ex, HttpServletRequest request) {
                logger.warn("Recurso não encontrado para a requisição {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_FOUND,
                        "O recurso solicitado não foi encontrado.",
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }

        // Exemplo de exceção customizada (crie esta classe se necessário)
        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ErrorResponse> handleBusinessException(
                BusinessException ex, HttpServletRequest request) {
                logger.warn("Erro de negócio em {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST, // Ou outro status apropriado
                        ex.getMessage(),
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(ResourceNotFoundException.class) // Assumindo que você criou esta exceção
        public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
                ResourceNotFoundException ex, HttpServletRequest request) {
                logger.warn("Recurso específico não encontrado em {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage(),
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(
                AccessDeniedException ex, HttpServletRequest request) {
                logger.warn("Acesso negado para a requisição {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.FORBIDDEN,
                        "Acesso negado. Você não tem permissão para realizar esta operação.",
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        }

        // Handler para SecurityException (acesso negado em regra de negócio)
        @ExceptionHandler(SecurityException.class)
        public ResponseEntity<ErrorResponse> handleSecurityException(
                SecurityException ex, HttpServletRequest request) {
                logger.warn("Tentativa de acesso negado em {}: {}", request.getRequestURI(), ex.getMessage());
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.FORBIDDEN,
                        ex.getMessage(), // Mensagem da exceção (ex: "Acesso negado ao status desta mensagem.")
                        request.getRequestURI()
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        }

        // Handler genérico
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGlobalException(
                        Exception ex, HttpServletRequest request) {
                logger.error("Erro interno inesperado na requisição {}:", request.getRequestURI(), ex);
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Ocorreu um erro interno inesperado no servidor. Verifique os logs para detalhes.",
                                request.getRequestURI());
                return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
}
