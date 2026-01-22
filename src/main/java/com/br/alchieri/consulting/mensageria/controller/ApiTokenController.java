package com.br.alchieri.consulting.mensageria.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.dto.request.ApiKeyDTO;
import com.br.alchieri.consulting.mensageria.dto.request.CreateApiKeyRequest;
import com.br.alchieri.consulting.mensageria.dto.response.ApiKeyCreationResponse;
import com.br.alchieri.consulting.mensageria.model.ApiKey;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.service.ApiKeyService;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "Gerenciamento de chaves de API (Stateful) para integrações.")
@RequiredArgsConstructor
@Slf4j
public class ApiTokenController {

    private final ApiKeyService apiKeyService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'ADMIN')")
    @Operation(summary = "Criar Nova API Key", description = "Gera uma chave secreta que deve ser usada no header 'X-API-KEY'. A chave é exibida apenas uma vez.")
    public ResponseEntity<ApiKeyCreationResponse> createApiKey(@RequestBody CreateApiKeyRequest request) {
        User currentUser = securityUtils.getAuthenticatedUser();
        
        String rawKey = apiKeyService.createApiKey(currentUser, request.getName(), request.getDaysToExpire());

        return ResponseEntity.ok(new ApiKeyCreationResponse(
            rawKey,
            "ATENÇÃO: Copie esta chave agora. Ela não será mostrada novamente."
        ));
    }

    @GetMapping
    @Operation(summary = "Listar API Keys", description = "Lista as chaves ativas do usuário.")
    public ResponseEntity<List<ApiKeyDTO>> listKeys() {
        User currentUser = securityUtils.getAuthenticatedUser();
        List<ApiKey> keys = apiKeyService.listKeys(currentUser);

        List<ApiKeyDTO> dtos = keys.stream().map(k -> new ApiKeyDTO(
            k.getId(),
            k.getName(),
            k.getKeyPrefix() + "...",
            k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : "Nunca",
            k.getCreatedAt().toString()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revogar API Key", description = "Invalida uma chave imediatamente.")
    public ResponseEntity<Void> revokeKey(@PathVariable Long id) {
        User currentUser = securityUtils.getAuthenticatedUser();
        apiKeyService.revokeApiKey(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
