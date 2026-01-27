package com.br.alchieri.consulting.mensageria.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.UploadPublicKeyRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.service.PlatformConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/platform-config")
@Tag(name = "Platform Configuration (Admin)", description = "Endpoints para administradores BSP configurarem a integração com a Meta.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('BSP_ADMIN')")
@RequiredArgsConstructor
public class AdminConfigController {

    private final PlatformConfigService platformConfigService;
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    @PostMapping("/upload-flow-public-key")
    @Operation(summary = "Upload da Chave Pública do Flow Endpoint",
               description = "Faz o upload de uma chave pública no formato PEM para a Meta e retorna o ID da chave gerado. " +
                             "Este ID deve ser salvo e configurado na aplicação (ex: na variável de ambiente FLOW_PUBLIC_KEY_ID). " +
                             "Esta operação só precisa ser feita uma vez, ou quando a chave for rotacionada.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chave pública registrada com sucesso.",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Erro da API Meta ou dados inválidos.",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Acesso Proibido.", content = @Content)
    })
    public ResponseEntity<ApiResponse> uploadFlowPublicKey(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Chave pública em formato PEM.", required = true,
                                content = @Content(schema = @Schema(implementation = UploadPublicKeyRequest.class)))
            @Valid @RequestBody UploadPublicKeyRequest request) {

        platformConfigService.uploadFlowPublicKey(request.getPublicKey(), request.getCompanyId());
        
        return ResponseEntity.ok(
            new ApiResponse(true, "Chave pública registrada com sucesso.", Map.of())
        );
    }

    @GetMapping("/flow-public-key-id")
    @Operation(summary = "Obter ID da Chave Pública do Flow")
    public ResponseEntity<ApiResponse> getFlowPublicKeyId(@RequestParam Long companyId) {
        String keyId = platformConfigService.getFlowPublicKeyId(companyId);
        return ResponseEntity.ok(
            new ApiResponse(true, "ID da chave pública recuperado.", Map.of("publicKeyId", keyId))
        );
    }
}
