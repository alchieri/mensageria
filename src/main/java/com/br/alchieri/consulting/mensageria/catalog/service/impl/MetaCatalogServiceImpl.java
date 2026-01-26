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
import com.br.alchieri.consulting.mensageria.catalog.model.ProductSet;
import com.br.alchieri.consulting.mensageria.catalog.repository.CatalogRepository;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductRepository;
import com.br.alchieri.consulting.mensageria.catalog.repository.ProductSetRepository;
import com.br.alchieri.consulting.mensageria.catalog.service.MetaCatalogService;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.exception.ResourceNotFoundException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.MetaBusinessManager;
import com.br.alchieri.consulting.mensageria.repository.MetaBusinessManagerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetaCatalogServiceImpl implements MetaCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(MetaCatalogServiceImpl.class);
    
    private final WebClient.Builder webClientBuilder;

    private final CatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final MetaBusinessManagerRepository businessManagerRepository;
    private final ProductSetRepository productSetRepository;

    private final ObjectMapper objectMapper;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String systemAccessToken;

    @Override
    @Transactional
    public Mono<Catalog> createCatalog(String catalogName, String vertical, Long metaBusinessManagerId, Company company) {
        
        // 1. Busca o BM específico
        MetaBusinessManager bm = businessManagerRepository.findById(metaBusinessManagerId)
                .orElseThrow(() -> new BusinessException("Business Manager não encontrado."));

        if (!bm.getCompany().getId().equals(company.getId())) {
            return Mono.error(new BusinessException("Este Business Manager não pertence à sua empresa."));
        }

        // 2. Prepara a chamada para a Meta API
        // Endpoint: POST /{business_id}/owned_product_catalogs
        String endpoint = graphApiBaseUrl + "/" + bm.getMetaBusinessId() + "/owned_product_catalogs";

        // Se o vertical não for informado, usa "commerce" como padrão seguro
        String finalVertical = (vertical != null && !vertical.isBlank()) ? vertical : "commerce";
        
        Map<String, String> body = new HashMap<>();
        body.put("name", catalogName);
        body.put("vertical", finalVertical);

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    String metaCatalogId = response.get("id").asText();
                    
                    Catalog catalog = new Catalog();
                    catalog.setName(catalogName);
                    catalog.setVertical(finalVertical);
                    catalog.setCompany(company);
                    catalog.setBusinessManager(bm); 
                    catalog.setMetaCatalogId(metaCatalogId);
                    
                    if (catalogRepository.findByCompanyAndIsDefaultTrue(company).isEmpty()) {
                        catalog.setDefault(true);
                    }
                    
                    Catalog savedCatalog = catalogRepository.save(catalog);

                    // --- VINCULAR AO WABA E AO NÚMERO ---
                    // Encadeia as chamadas de vínculo
                    return connectCatalogToWaba(company, metaCatalogId)
                            .then(connectCatalogToPhoneNumber(company, metaCatalogId))
                            .thenReturn(savedCatalog);
                })
                .doOnSuccess(c -> logger.info("Catálogo criado e vinculado: {} (Meta ID: {})", c.getName(), c.getMetaCatalogId()))
                .doOnError(e -> logger.error("Erro no fluxo de criação de catálogo: {}", e.getMessage()));
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
                MetaSyncDTOs.MetaCatalogListResponse response = webClientBuilder.build().get()
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

                        try {
                            connectCatalogToWaba(company, catalog.getMetaCatalogId()).block();
                            connectCatalogToPhoneNumber(company, catalog.getMetaCatalogId()).block();
                        } catch (Exception e) {
                            logger.warn("Não foi possível vincular automaticamente o catálogo {} durante o sync: {}", catalog.getName(), e.getMessage());
                        }

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

    @Override
    @Transactional
    public Mono<ProductSet> createProductSet(Long catalogId, String name, List<String> retailerIds, Company company) {
        
        // 1. Validar Catálogo
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado."));

        if (!catalog.getCompany().getId().equals(company.getId())) {
            return Mono.error(new BusinessException("Acesso negado ao catálogo."));
        }
        
        if (catalog.getMetaCatalogId() == null) {
            return Mono.error(new BusinessException("Catálogo não possui ID da Meta vinculado."));
        }

        // 2. Construir o Filtro
        // Estrutura da Meta: {"retailer_id": {"is_any": ["id1", "id2"]}}
        Map<String, Object> filterMap = new HashMap<>();
        
        if (retailerIds != null && !retailerIds.isEmpty()) {
            Map<String, Object> condition = new HashMap<>();
            condition.put("is_any", retailerIds);
            filterMap.put("retailer_id", condition);
        } else {
            // Se não passar IDs, cria um set que inclui tudo (ou lógica de negócio específica)
            // Cuidado: Sets vazios podem dar erro dependendo do uso.
            // Vamos assumir filtro por categoria ou marca se necessário, aqui exemplo simplificado:
            return Mono.error(new BusinessException("É necessário informar ao menos um produto para o conjunto."));
        }

        String filterJson;
        try {
            filterJson = objectMapper.writeValueAsString(filterMap);
        } catch (JsonProcessingException e) {
            return Mono.error(new BusinessException("Erro ao processar filtros do conjunto."));
        }

        // 3. Preparar Request para Meta
        String endpoint = graphApiBaseUrl + "/" + catalog.getMetaCatalogId() + "/product_sets";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("filter", filterJson); // A Meta espera o filtro como JSON String ou Objeto dependendo da versão, WebClient com BodyInserters geralmente serializa mapas corretamente, mas 'filter' é um campo especial.
        // Na Graph API v18+, 'filter' deve ser passado como objeto JSON dentro do body.

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body)) // O Jackson vai serializar o body
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    String metaSetId = response.get("id").asText();
                    
                    ProductSet productSet = new ProductSet();
                    productSet.setCatalog(catalog);
                    productSet.setName(name);
                    productSet.setMetaProductSetId(metaSetId);
                    productSet.setFilterDefinition(filterJson);
                    
                    return productSetRepository.save(productSet);
                })
                .doOnSuccess(ps -> logger.info("Product Set '{}' criado com sucesso. Meta ID: {}", ps.getName(), ps.getMetaProductSetId()))
                .doOnError(e -> logger.error("Erro ao criar Product Set na Meta: {}", e.getMessage()));
    }

    /**
     * Vincula o Catálogo à WABA (WhatsApp Business Account).
     * POST /{WABA_ID}/product_catalogs
     */
    private Mono<Void> connectCatalogToWaba(Company company, String metaCatalogId) {
        if (company.getMetaWabaId() == null) {
            logger.warn("WABA ID não configurado para empresa {}. Pulo vínculo WABA.", company.getName());
            return Mono.empty();
        }

        String endpoint = graphApiBaseUrl + "/" + company.getMetaWabaId() + "/product_catalogs";
        Map<String, String> body = new HashMap<>();
        body.put("catalog_id", metaCatalogId);

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(json -> logger.info("Catálogo {} vinculado à WABA {}", metaCatalogId, company.getMetaWabaId()))
                .doOnError(e -> logger.error("Falha ao vincular catálogo à WABA: {}", e.getMessage()))
                .then();
    }

    /**
     * Vincula o Catálogo ao Número de Telefone (para exibir na loja/perfil).
     * POST /{PHONE_NUMBER_ID}/whatsapp_business_catalogs
     */
    private Mono<Void> connectCatalogToPhoneNumber(Company company, String metaCatalogId) {
        if (company.getMetaPrimaryPhoneNumberId() == null) {
            logger.warn("Phone Number ID não configurado. Pulo vínculo de número.");
            return Mono.empty();
        }

        String endpoint = graphApiBaseUrl + "/" + company.getMetaPrimaryPhoneNumberId() + "/whatsapp_business_catalogs";
        Map<String, String> body = new HashMap<>();
        body.put("catalog_id", metaCatalogId);

        return webClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(json -> logger.info("Catálogo {} vinculado ao Número {}", metaCatalogId, company.getMetaPrimaryPhoneNumberId()))
                .doOnError(e -> logger.error("Falha ao vincular catálogo ao número: {}", e.getMessage()))
                .then();
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