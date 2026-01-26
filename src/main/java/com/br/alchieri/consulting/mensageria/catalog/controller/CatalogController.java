package com.br.alchieri.consulting.mensageria.catalog.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.service.MetaCatalogService;
import com.br.alchieri.consulting.mensageria.dto.response.ApiResponse;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.util.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog Management", description = "Gestão de Produtos no Catálogo Meta (Marketing API).")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CatalogController {

    private final MetaCatalogService metaCatalogService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Criar Catálogo", description = "Cria um novo catálogo na Meta e vincula à empresa.")
    public ResponseEntity<ApiResponse> createCatalog(@RequestBody Map<String, String> payload) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company company = currentUser.getCompany();
        
        String name = payload.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Nome do catálogo é obrigatório.", null));
        }

        Catalog createdCatalog = metaCatalogService.createCatalog(name, company).block();

        return ResponseEntity.ok(new ApiResponse(true, "Catálogo criado com sucesso.", createdCatalog));
    }

    @PostMapping("/products")
    @Operation(summary = "Sincronizar Produtos (Upsert)", description = "Cria ou Atualiza produtos no catálogo da empresa.")
    public ResponseEntity<ApiResponse> upsertProducts(@RequestBody @Valid List<ProductSyncRequest> products) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company company = currentUser.getCompany();

        if (company == null) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Usuário não vinculado a uma empresa.", null));
        }

        metaCatalogService.upsertProducts(products, company).block();

        return ResponseEntity.ok(new ApiResponse(true, "Produtos enviados para processamento no catálogo.", null));
    }

    @DeleteMapping("/products")
    @Operation(summary = "Deletar Produtos", description = "Remove produtos do catálogo informando os SKUs.")
    public ResponseEntity<ApiResponse> deleteProducts(@RequestBody List<String> skus) {
        User currentUser = securityUtils.getAuthenticatedUser();
        Company company = currentUser.getCompany();

        metaCatalogService.deleteProducts(skus, company).block();

        return ResponseEntity.ok(new ApiResponse(true, "Solicitação de exclusão enviada.", null));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sincronizar Catálogos", description = "Busca catálogos existentes na conta da Meta e cria/atualiza no banco local.")
    public ResponseEntity<ApiResponse> syncCatalogs() {
        User user = securityUtils.getAuthenticatedUser();
        metaCatalogService.syncCatalogsFromMeta(user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Sincronização de catálogos iniciada.", null));
    }

    @PostMapping("/{catalogId}/products/sync")
    @Operation(summary = "Sincronizar Produtos", description = "Busca produtos de um catálogo específico na Meta e cria/atualiza no banco local.")
    public ResponseEntity<ApiResponse> syncProducts(@PathVariable Long catalogId) {
        User user = securityUtils.getAuthenticatedUser();
        metaCatalogService.syncProductsFromMeta(catalogId, user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Sincronização de produtos iniciada.", null));
    }
}
