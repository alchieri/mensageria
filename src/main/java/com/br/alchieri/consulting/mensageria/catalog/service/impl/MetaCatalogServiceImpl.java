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
import com.br.alchieri.consulting.mensageria.repository.CompanyRepository;
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

    @Override
    @Transactional
    public void syncCatalogsFromMeta(Company company) {
        
        String businessId = company.getFacebookBusinessManagerId();

        if (businessId == null || businessId.isBlank()) {
            throw new BusinessException("ID do Business Manager não configurado para a empresa.");
        }

        String url = graphApiBaseUrl + "/" + businessId + "/owned_product_catalogs";

        try {
            MetaSyncDTOs.MetaCatalogListResponse response = webClient.get()
                    .uri(url + "?access_token=" + systemAccessToken)
                    .retrieve()
                    .bodyToMono(MetaSyncDTOs.MetaCatalogListResponse.class)
                    .block();

            if (response != null && response.getData() != null) {
                for (MetaSyncDTOs.MetaCatalogData metaCat : response.getData()) {
                    // Verifica se já existe localmente pelo ID da Meta
                    Optional<Catalog> existing = catalogRepository.findByMetaCatalogId(metaCat.getId());
                    
                    Catalog catalog;
                    if (existing.isPresent()) {
                        catalog = existing.get();
                        catalog.setName(metaCat.getName()); // Atualiza nome se mudou
                    } else {
                        catalog = new Catalog();
                        catalog.setCompany(company);
                        catalog.setMetaCatalogId(metaCat.getId());
                        catalog.setName(metaCat.getName());
                        catalog.setDefault(false); // Default logic to check
                    }
                    catalogRepository.save(catalog);
                }
                logger.info("Sincronização de catálogos concluída. Total: {}", response.getData().size());
            }

        } catch (Exception e) {
            logger.error("Erro ao buscar catálogos da Meta: ", e);
            throw new BusinessException("Falha ao sincronizar catálogos: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void syncProductsFromMeta(Long localCatalogId, Company company) {
        Catalog catalog = catalogRepository.findById(localCatalogId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        if (!catalog.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("Acesso negado ao catálogo.");
        }
        
        if (catalog.getMetaCatalogId() == null) {
            throw new BusinessException("Este catálogo não possui vínculo com a Meta (ID externo ausente).");
        }

        String url = graphApiBaseUrl + "/" + catalog.getMetaCatalogId() + "/products";
        // Campos que queremos buscar
        String fields = "id,retailer_id,name,description,price,image_url,availability";

        // Monta a URL inicial
        String currentUrl = String.format("%s?access_token=%s&fields=%s&limit=100", url, systemAccessToken, fields);

        logger.info("Iniciando sincronização de produtos para o catálogo: {}", catalog.getName());

        int pageCount = 0;
        int totalProductsSynced = 0;
        boolean hasNext = true;

        while (hasNext) {
            try {
                // Como a URL next vem completa da Meta, passamos direto no uri()
                // Na primeira iteração, usamos a URL montada manualmente.
                String urlToCall = currentUrl; 

                MetaSyncDTOs.MetaProductListResponse response = webClient.get()
                        .uri(urlToCall)
                        .retrieve()
                        .bodyToMono(MetaSyncDTOs.MetaProductListResponse.class)
                        .block();

                if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                    // Processa o lote atual
                    processProductBatch(catalog, response.getData());
                    
                    totalProductsSynced += response.getData().size();
                    pageCount++;
                    logger.debug("Página {} processada. Produtos acumulados: {}", pageCount, totalProductsSynced);

                    // Verifica se existe próxima página
                    if (response.getPaging() != null && response.getPaging().getNext() != null) {
                        currentUrl = response.getPaging().getNext();
                    } else {
                        hasNext = false;
                    }
                } else {
                    hasNext = false; // Nenhum dado retornado, encerra loop
                }

                // Safety Break: Evitar loops infinitos em caso de erro da API
                if (pageCount > 500) { // Limite arbitrário de 50k produtos (500 * 100)
                    logger.warn("Limite de segurança de páginas atingido (500). Interrompendo sincronização.");
                    break;
                }

            } catch (Exception e) {
                logger.error("Erro ao buscar página de produtos da Meta na iteração {}: {}", pageCount, e.getMessage());
                // Decide se aborta tudo ou tenta continuar (aqui optamos por abortar para garantir integridade)
                throw new BusinessException("Falha na sincronização paginada: " + e.getMessage());
            }
        }

        logger.info("Sincronização concluída. Total de produtos processados: {}", totalProductsSynced);
    }

    /**
     * Método auxiliar para processar e salvar/atualizar a lista de produtos.
     */
    private void processProductBatch(Catalog catalog, List<MetaSyncDTOs.MetaProductData> metaProducts) {
        
        for (MetaSyncDTOs.MetaProductData metaProd : metaProducts) {
            // Validação básica: Produto precisa de SKU (retailer_id)
            if (metaProd.getRetailerId() == null) {
                continue; 
            }

            Optional<Product> existing = productRepository.findByCatalogAndSku(catalog, metaProd.getRetailerId());

            Product product;
            if (existing.isPresent()) {
                product = existing.get();
            } else {
                product = new Product();
                product.setCatalog(catalog);
                product.setSku(metaProd.getRetailerId());
            }

            // product.setFacebookProductId(metaProd.getId());
            product.setName(metaProd.getName());
            product.setCurrency(metaProd.getCurrency());
            // Limita descrição se necessário para caber no banco
            String desc = metaProd.getDescription();
            if (desc != null && desc.length() > 1000) desc = desc.substring(0, 1000);
            product.setDescription(desc);
            
            product.setImageUrl(metaProd.getImageUrl());
            product.setInStock("in stock".equalsIgnoreCase(metaProd.getAvailability()));
            product.setPrice(parsePrice(metaProd.getPrice()));

            productRepository.save(product);
        }
    }

    // Helper para extrair numérico de strings como "100.50 BRL"
    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null) return BigDecimal.ZERO;
        try {
            // Remove tudo que não é numero ou ponto
            String clean = priceStr.replaceAll("[^0-9.]", "");
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
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