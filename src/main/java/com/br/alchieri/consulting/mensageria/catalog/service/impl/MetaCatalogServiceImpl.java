package com.br.alchieri.consulting.mensageria.catalog.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.br.alchieri.consulting.mensageria.catalog.dto.meta.BatchItem;
import com.br.alchieri.consulting.mensageria.catalog.dto.meta.MetaProductBatchRequest;
import com.br.alchieri.consulting.mensageria.catalog.dto.meta.ProductAttributes;
import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.repository.CatalogRepository;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.catalog.service.MetaCatalogService;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetaCatalogServiceImpl implements MetaCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(MetaCatalogServiceImpl.class);
    
    private final WebClient.Builder webClientBuilder;
    private final CatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String systemAccessToken;

    @Override
    @Transactional
    public Mono<Catalog> createCatalog(String catalogName, Company company) {
        if (company.getMetaBusinessId() == null || company.getMetaBusinessId().isBlank()) {
            return Mono.error(new BusinessException("Empresa não possui Business ID configurado para criar catálogos."));
        }

        String endpoint = graphApiBaseUrl + "/" + company.getMetaBusinessId() + "/owned_product_catalogs";
        Map<String, String> body = new HashMap<>();
        body.put("name", catalogName);

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String metaId = response.get("id").asText();
                    
                    // Salvar no Banco Local
                    Catalog catalog = new Catalog();
                    catalog.setName(catalogName);
                    catalog.setCompany(company);
                    catalog.setMetaCatalogId(metaId);
                    
                    // Se não houver padrão, marca este como padrão
                    if (catalogRepository.findByCompanyAndIsDefaultTrue(company).isEmpty()) {
                        catalog.setDefault(true);
                        // Atualiza a referencia rápida na Company também (opcional, mantendo compatibilidade)
                        company.setMetaCatalogId(metaId);
                        companyRepository.save(company);
                    }
                    
                    return catalogRepository.save(catalog);
                })
                .doOnSuccess(c -> logger.info("Catálogo '{}' criado com ID Meta {}", c.getName(), c.getMetaCatalogId()));
    }

    @Override
    @Transactional
    public Mono<Void> upsertProducts(List<ProductSyncRequest> productsRequest, Company company) {
        // 1. Obter Catálogo Padrão (ou criar lógica para selecionar qual catálogo)
        Catalog catalog = catalogRepository.findByCompanyAndIsDefaultTrue(company)
                .orElseThrow(() -> new BusinessException("Nenhum catálogo padrão encontrado para esta empresa. Crie um catálogo primeiro."));

        // 2. Persistir Produtos Localmente (Espelhamento)
        List<BatchItem> batchItems = productsRequest.stream().map(dto -> {
            
            // Lógica de Banco Local
            Product product = productRepository.findByCatalogAndSku(catalog, dto.getSku())
                    .orElse(new Product());
            
            product.setCatalog(catalog);
            product.setSku(dto.getSku());
            product.setName(dto.getName());
            product.setDescription(dto.getDescription());
            product.setPrice(BigDecimal.valueOf(dto.getPrice()));
            product.setCurrency(dto.getCurrency());
            product.setImageUrl(dto.getImageUrl());
            product.setWebsiteUrl(dto.getWebsiteUrl());
            product.setBrand(dto.getBrand());
            product.setInStock(dto.isInStock());
            
            productRepository.save(product);

            // Mapeamento para Meta Batch API
            ProductAttributes attrs = ProductAttributes.builder()
                    .name(dto.getName())
                    .description(dto.getDescription() != null ? dto.getDescription() : dto.getName())
                    .availability(dto.isInStock() ? "in stock" : "out of stock")
                    .condition("new")
                    .priceAmount((long) (dto.getPrice() * 100))
                    .currency(dto.getCurrency())
                    .brand(dto.getBrand())
                    .url(dto.getWebsiteUrl())
                    .imageUrl(dto.getImageUrl())
                    .build();

            return BatchItem.builder()
                    .method("UPDATE")
                    .retailerId(dto.getSku())
                    .data(attrs)
                    .build();
        }).collect(Collectors.toList());

        // 3. Enviar para a Meta
        return sendBatchRequest(catalog.getMetaCatalogId(), batchItems);
    }

    @Override
    @Transactional
    public Mono<Void> deleteProducts(List<String> skus, Company company) {
        Catalog catalog = catalogRepository.findByCompanyAndIsDefaultTrue(company)
                .orElseThrow(() -> new BusinessException("Catálogo não encontrado."));

        // Remove do Banco Local
        skus.forEach(sku -> {
            productRepository.findByCatalogAndSku(catalog, sku).ifPresent(productRepository::delete);
        });

        // Remove da Meta
        List<BatchItem> batchItems = skus.stream().map(sku -> BatchItem.builder()
                .method("DELETE")
                .retailerId(sku)
                .build()
        ).collect(Collectors.toList());

        return sendBatchRequest(catalog.getMetaCatalogId(), batchItems);
    }

    private Mono<Void> sendBatchRequest(String catalogId, List<BatchItem> items) {
        MetaProductBatchRequest request = MetaProductBatchRequest.builder().requests(items).build();
        String endpoint = graphApiBaseUrl + "/" + catalogId + "/batch";

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(r -> logger.info("Sincronização com Meta concluída para o catálogo {}", catalogId))
                .doOnError(e -> logger.error("Erro na sincronização Meta: {}", e.getMessage()))
                .then();
    }
}