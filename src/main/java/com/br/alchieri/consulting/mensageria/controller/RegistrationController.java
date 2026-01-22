package com.br.alchieri.consulting.mensageria.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.PublicRegistrationRequest;
import com.br.alchieri.consulting.mensageria.dto.response.RegistrationResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.service.RegistrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/register")
@Tag(name = "Registration", description = "Endpoint público para registro de novas empresas.")
@RequiredArgsConstructor
public class RegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    private final RegistrationService registrationService; // Injeta o novo serviço

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar Nova Empresa e Usuário Admin",
               description = "Permite que um novo cliente se registre na plataforma, criando uma conta de empresa e um usuário administrador para essa empresa.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Empresa e usuário registrados com sucesso.",
                    content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos ou empresa/usuário já existente.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro interno do servidor.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<?> registerNewCompanyAndUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dados da nova empresa e do seu administrador.", required = true,
                                content = @Content(schema = @Schema(implementation = PublicRegistrationRequest.class)))
            @Valid @RequestBody PublicRegistrationRequest request
    ) {
        logger.info("Recebida requisição de registro para a empresa: {}", request.getCompany().getName());
        try {
            RegistrationResponse response = registrationService.registerNewCompanyAndUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (BusinessException e) {
            // O GlobalExceptionHandler pegará isso e retornará um 400 Bad Request com a mensagem
            logger.warn("Falha no registro da empresa {}: {}", request.getCompany().getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado durante o registro da empresa {}: {}", request.getCompany().getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new com.br.alchieri.consulting.mensageria.dto.response.ApiResponse(false, "Erro inesperado durante o registro.", null));
        }
    }
}
