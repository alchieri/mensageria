package com.br.alchieri.consulting.mensageria.catalog.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.br.alchieri.consulting.mensageria.catalog.dto.meta.MetaSyncDTOs;
import com.br.alchieri.consulting.mensageria.catalog.dto.meta.ProductAttributes;
import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.repository.CatalogRepository;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.catalog.service.MetaCatalogService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.MetaBusinessManager;
import com.br.alchieri.consulting.mensageria.repository.MetaBusinessManagerRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetaCatalogServiceImpl implements MetaCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(MetaCatalogServiceImpl.class);
    
    private final WebClient.Builder webClientBuilder;
    private final WebClient webClient = WebClient.create();

    private final CatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final MetaBusinessManagerRepository businessManagerRepository;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String systemAccessToken;

    @Override
    @Transactional
    public Mono<Catalog> createCatalog(String catalogName, Long metaBusinessManagerId, Company company) {
        
        // 1. Busca o BM específico
        MetaBusinessManager bm = businessManagerRepository.findById(metaBusinessManagerId)
                .orElseThrow(() -> new BusinessException("Business Manager não encontrado."));

        if (!bm.getCompany().getId().equals(company.getId())) {
            return Mono.error(new BusinessException("Este Business Manager não pertence à sua empresa."));
        }

        // URL: /{business_id}/owned_product_catalogs
        String endpoint = graphApiBaseUrl + "/" + bm.getMetaBusinessId() + "/owned_product_catalogs";
        
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
                    
                    Catalog catalog = new Catalog();
                    catalog.setName(catalogName);
                    catalog.setCompany(company);
                    catalog.setBusinessManager(bm); // Vincula ao BM
                    catalog.setMetaCatalogId(metaId);
                    
                    if (catalogRepository.findByCompanyAndIsDefaultTrue(company).isEmpty()) {
                        catalog.setDefault(true);
                    }
                    
                    return catalogRepository.save(catalog);
                })
                .doOnSuccess(c -> logger.info("Catálogo '{}' criado no BM {} (Meta ID: {})", c.getName(), bm.getName(), c.getMetaCatalogId()));
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

    @Override
    @Transactional
    public void syncCatalogsFromMeta(Company company) {
        
        // 1. Buscar todos os BMs da empresa
        List<MetaBusinessManager> bms = businessManagerRepository.findByCompany(company);

        if (bms.isEmpty()) {
            throw new BusinessException("Nenhum Business ID vinculado. Execute a sincronização de empresa primeiro.");
        }

        int totalSynced = 0;

        // 2. Iterar sobre cada BM para buscar seus catálogos
        for (MetaBusinessManager bm : bms) {
            logger.info("Sincronizando catálogos do Business: {} ({})", bm.getName(), bm.getMetaBusinessId());
            
            String url = graphApiBaseUrl + "/" + bm.getMetaBusinessId() + "/owned_product_catalogs";

            try {
                MetaSyncDTOs.MetaCatalogListResponse response = webClient.get()
                        .uri(url + "?access_token=" + systemAccessToken)
                        .retrieve()
                        .bodyToMono(MetaSyncDTOs.MetaCatalogListResponse.class)
                        .block();

                if (response != null && response.getData() != null) {
                    for (MetaSyncDTOs.MetaCatalogData metaCat : response.getData()) {
                        
                        Optional<Catalog> existing = catalogRepository.findByMetaCatalogId(metaCat.getId());
                        
                        Catalog catalog;
                        if (existing.isPresent()) {
                            catalog = existing.get();
                            catalog.setName(metaCat.getName());
                        } else {
                            catalog = new Catalog();
                            catalog.setBusinessManager(bm);
                            catalog.setMetaCatalogId(metaCat.getId());
                            catalog.setName(metaCat.getName());
                            catalog.setDefault(false);
                        }
                        
                        // VINCULA AO BM CORRETO
                        catalog.setBusinessManager(bm);
                        
                        catalogRepository.save(catalog);
                        totalSynced++;
                    }
                }
            } catch (Exception e) {
                logger.error("Erro ao buscar catálogos do BM {}: {}", bm.getMetaBusinessId(), e.getMessage());
                // Continua para o próximo BM mesmo se um falhar
            }
        }
        logger.info("Sincronização concluída. Total de catálogos: {}", totalSynced);
    }

    @Override
    @Transactional
    public void syncProductsFromMeta(Long localCatalogId, Company company) {
        Catalog catalog = catalogRepository.findById(localCatalogId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        if (!catalog.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("Acesso negado ao catálogo.");
        }
        
        // Validação extra: O catálogo tem um ID da Meta?
        if (catalog.getMetaCatalogId() == null) {
            throw new BusinessException("Catálogo não vinculado à Meta.");
        }

        String url = graphApiBaseUrl + "/" + catalog.getMetaCatalogId() + "/products";
        String fields = "id,retailer_id,name,description,price,image_url,availability,currency";

        // Paginação simplificada (pode evoluir para loop reactivo com expand())
        webClientBuilder.build().get()
                .uri(uriBuilder -> uriBuilder
                    .path(url) // Se url for completa, use .uri(url)
                    .queryParam("access_token", systemAccessToken)
                    .queryParam("fields", fields)
                    .queryParam("limit", 100)
                    .build())
                .retrieve()
                .bodyToMono(MetaSyncDTOs.MetaProductListResponse.class)
                .subscribe(response -> {
                    if (response.getData() != null) {
                        processProductBatchSync(catalog, response.getData());
                    }
                }, error -> logger.error("Erro ao sincronizar produtos: ", error));
    }

    private void processProductBatchSync(Catalog catalog, List<MetaSyncDTOs.MetaProductData> metaProducts) {
        for (MetaSyncDTOs.MetaProductData metaProd : metaProducts) {
            if (metaProd.getRetailerId() == null) continue;

            Product product = productRepository.findByCatalogAndSku(catalog, metaProd.getRetailerId())
                    .orElse(new Product());

            product.setCatalog(catalog);
            product.setSku(metaProd.getRetailerId());
            product.setName(metaProd.getName());
            product.setDescription(metaProd.getDescription());
            product.setImageUrl(metaProd.getImageUrl());
            product.setInStock("in stock".equalsIgnoreCase(metaProd.getAvailability()));
            
            // Tratamento de preço
            try {
                if (metaProd.getPrice() != null) {
                    // Meta manda "100 BRL" ou numérico
                    String p = metaProd.getPrice().replaceAll("[^0-9.]", "");
                    product.setPrice(new BigDecimal(p));
                }
                product.setCurrency(metaProd.getCurrency());
            } catch (Exception e) {
                logger.warn("Erro ao parsear preço do produto {}: {}", metaProd.getRetailerId(), e.getMessage());
            }

            productRepository.save(product);
        }
        logger.info("Lote de produtos sincronizado para o catálogo {}", catalog.getId());
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