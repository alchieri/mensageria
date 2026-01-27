package com.br.alchieri.consulting.mensageria.catalog.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.alchieri.consulting.mensageria.catalog.dto.request.CreateCatalogRequest;
import com.br.alchieri.consulting.mensageria.catalog.dto.request.CreateProductSetRequest;
import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.model.ProductSet;
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
    public ResponseEntity<ApiResponse> createCatalog(@Valid @RequestBody CreateCatalogRequest request) {
        
        User currentUser = securityUtils.getAuthenticatedUser();

        Catalog catalog = metaCatalogService.createCatalog(
                request.getName(), 
                request.getVertical(),
                request.getMetaBusinessManagerId(), 
                currentUser.getCompany()
        ).block();

        return ResponseEntity.ok(new ApiResponse(true, "Catálogo criado com sucesso na Meta.", catalog));
    }

    @PostMapping("/{catalogId}/products")
    @Operation(summary = "Sincronizar Produtos (Upsert)", description = "Cria ou Atualiza produtos no catálogo da empresa.")
    public ResponseEntity<ApiResponse> upsertProducts(@PathVariable Long catalogId, 
                @RequestBody @Valid List<ProductSyncRequest> dtos) {
        
        User currentUser = securityUtils.getAuthenticatedUser();
        Company company = currentUser.getCompany();

        if (company == null) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Usuário não vinculado a uma empresa.", null));
        }

        List<Product> products = dtos.stream().map(dto -> {
            Product p = new Product();
            p.setSku(dto.getSku());
            p.setName(dto.getName());
            p.setDescription(dto.getDescription());
            p.setImageUrl(dto.getImageUrl());
            p.setWebsiteUrl(dto.getWebsiteUrl());
            p.setBrand(dto.getBrand());
            p.setInStock(dto.isInStock());
            p.setCurrency(dto.getCurrency());
            if (dto.getPrice() != null) {
                p.setPrice(BigDecimal.valueOf(dto.getPrice()));
            }
            return p;
        }).collect(Collectors.toList());

        metaCatalogService.upsertProducts(catalogId, products);

        return ResponseEntity.ok(new ApiResponse(true, "Lote de produtos enviado para processamento assíncrono.", null));
    }

    @DeleteMapping("/{catalogId}/products")
    @Operation(summary = "Deletar Produtos", description = "Remove produtos do catálogo informando os SKUs.")
    public ResponseEntity<ApiResponse> deleteProducts(
            @PathVariable Long catalogId,
            @RequestBody List<String> skus) {
        
        metaCatalogService.deleteProducts(catalogId, skus);

        return ResponseEntity.ok(new ApiResponse(true, "Solicitação de exclusão enviada para processamento.", null));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sincronizar Catálogos", description = "Busca catálogos existentes na conta da Meta e cria/atualiza no banco local.")
    public ResponseEntity<ApiResponse> syncCatalogs() {
        User user = securityUtils.getAuthenticatedUser();
        metaCatalogService.syncCatalogsFromMeta(user.getCompany());
        return ResponseEntity.ok(new ApiResponse(true, "Sincronização de catálogos iniciada.", null));
    }

    @PostMapping("/{catalogId}/products/sync")
    @Operation(summary = "Sincronizar Produtos da Meta", description = "Busca produtos de um catálogo específico na Meta e cria/atualiza no banco local.")
    public ResponseEntity<ApiResponse> syncProducts(@PathVariable Long catalogId) {
        // A validação de empresa deve ser feita internamente ou via filtro, mas a assinatura mudou para apenas ID
        metaCatalogService.syncProductsFromMeta(catalogId);
        return ResponseEntity.ok(new ApiResponse(true, "Sincronização de produtos iniciada (Background).", null));
    }

    @PostMapping("/{catalogId}/product-sets")
    @Operation(summary = "Criar Product Set", description = "Cria um subconjunto de produtos dentro de um catálogo para uso em mensagens de lista.")
    public ResponseEntity<ApiResponse> createProductSet(
            @PathVariable Long catalogId,
            @Valid @RequestBody CreateProductSetRequest request) {
        
        User user = securityUtils.getAuthenticatedUser();
        
        ProductSet productSet = metaCatalogService.createProductSet(
                catalogId,
                request.getName(),
                request.getProductRetailerIds(),
                user.getCompany()
        ).block();

        return ResponseEntity.ok(new ApiResponse(true, "Product Set criado com sucesso.", productSet));
    }
}
