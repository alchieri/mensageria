package com.br.alchieri.consulting.mensageria.chat.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.chat.dto.response.VariableDictionaryResponse;
import com.br.alchieri.consulting.mensageria.chat.service.TemplateVariableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/template-variables")
@Tag(name = "Template Variables", description = "Endpoints para obter informações sobre variáveis dinâmicas.")
@SecurityRequirement(name = "bearerAuth") // Requer autenticação para saber quais variáveis estão disponíveis
@RequiredArgsConstructor
public class TemplateVariableController {

    private final TemplateVariableService templateVariableService;

    @GetMapping(value = "/dictionary", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obter Dicionário de Variáveis",
               description = "Retorna uma lista estruturada de todas as variáveis dinâmicas que podem ser usadas nos mapeamentos de templates (ex: 'contact.name', 'company.name').")
    @ApiResponse(responseCode = "200", description = "Dicionário retornado com sucesso.",
                 content = @Content(schema = @Schema(implementation = VariableDictionaryResponse.class)))
    public ResponseEntity<VariableDictionaryResponse> getVariableDictionary() {
        return ResponseEntity.ok(templateVariableService.getVariableDictionary());
    }
}
