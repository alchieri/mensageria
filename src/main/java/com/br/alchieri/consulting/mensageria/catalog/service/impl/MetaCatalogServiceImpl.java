package com.br.alchieri.consulting.mensageria.catalog.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;
import com.br.alchieri.consulting.mensageria.repository.MetaBusinessManagerRepository;
import com.br.alchieri.consulting.mensageria.repository.WhatsAppPhoneNumberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaCatalogServiceImpl implements MetaCatalogService {

    private final WebClient.Builder webClientBuilder;

    private final CatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final MetaBusinessManagerRepository businessManagerRepository;
    private final ProductSetRepository productSetRepository;
    private final WhatsAppPhoneNumberRepository phoneNumberRepository;

    private final ObjectMapper objectMapper;

    @Value("${whatsapp.graph-api.base-url}")
    private String graphApiBaseUrl;

    @Value("${whatsapp.api.token}")
    private String systemAccessToken;

    private static final int META_BATCH_SIZE = 100;

    @Override
    @Transactional
    public Mono<Catalog> createCatalog(String catalogName, String vertical, Long metaBusinessManagerId, Company company) {
        
        MetaBusinessManager bm = businessManagerRepository.findById(metaBusinessManagerId)
                .orElseThrow(() -> new BusinessException("Business Manager não encontrado."));

        if (!bm.getCompany().getId().equals(company.getId())) {
            return Mono.error(new BusinessException("Este Business Manager não pertence à sua empresa."));
        }

        String endpoint = graphApiBaseUrl + "/" + bm.getMetaBusinessId() + "/owned_product_catalogs";

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

                    return connectCatalogToWaba(company, metaCatalogId)
                            .then(connectCatalogToPhoneNumber(company, metaCatalogId))
                            .thenReturn(savedCatalog);
                })
                .doOnSuccess(c -> log.info("Catálogo criado e vinculado: {} (Meta ID: {})", c.getName(), c.getMetaCatalogId()))
                .doOnError(e -> log.error("Erro no fluxo de criação de catálogo: {}", e.getMessage()));
    }

    @Override
    @Transactional
    public void upsertProducts(Long catalogId, List<Product> products) {
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Catálogo não encontrado"));

        // 1. Atualiza no Banco Local
        List<Product> savedProducts = productRepository.saveAll(products);

        // 2. Prepara requests para a Meta
        List<BatchItem> allRequests = savedProducts.stream()
                .map(this::convertToBatchItem)
                .collect(Collectors.toList());

        // 3. Envia em Lotes (Partitioning) para respeitar limites da API
        List<List<BatchItem>> batches = Lists.partition(allRequests, META_BATCH_SIZE);

        for (List<BatchItem> batch : batches) {
            sendBatchRequest(catalog.getMetaCatalogId(), batch, systemAccessToken);
        }
    }

    @Override
    @Transactional
    public void deleteProducts(Long catalogId, List<String> skus) {
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Catálogo não encontrado"));
        
        // 1. Remove do Banco Local
        productRepository.deleteAllByCatalogIdAndSkuIn(catalogId, skus);

        // 2. Prepara requests de deleção para a Meta
        List<BatchItem> allRequests = skus.stream()
                .map(sku -> BatchItem.builder()
                        .method("DELETE")
                        .retailerId(sku)
                        .build())
                .collect(Collectors.toList());

        // 3. Envia em Lotes
        List<List<BatchItem>> batches = Lists.partition(allRequests, META_BATCH_SIZE);

        for (List<BatchItem> batch : batches) {
            sendBatchRequest(catalog.getMetaCatalogId(), batch, systemAccessToken);
        }
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
            log.info("Sincronizando catálogos do Business: {} ({})", bm.getName(), bm.getMetaBusinessId());
            
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
                            log.warn("Não foi possível vincular automaticamente o catálogo {} durante o sync: {}", catalog.getName(), e.getMessage());
                        }

                        totalSynced++;
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao buscar catálogos do BM {}: {}", bm.getMetaBusinessId(), e.getMessage());
                // Continua para o próximo BM mesmo se um falhar
            }
        }
        log.info("Sincronização concluída. Total de catálogos: {}", totalSynced);
    }

    @Override
    @Transactional
    public void syncProductsFromMeta(Long catalogId) {
        Catalog catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Catálogo não encontrado"));

        String metaCatalogId = catalog.getMetaCatalogId();

        // Conjunto para rastrear quais SKUs a Meta retornou
        Set<String> metaSkus = new HashSet<>();
        
        // Carrega SKUs locais para comparação posterior
        Set<String> localSkus = productRepository.findAllSkusByCatalogId(catalogId);

        log.info("Iniciando sincronização full para catálogo {}. SKUs locais atuais: {}", catalogId, localSkus.size());

        fetchProductsRecursive(metaCatalogId, systemAccessToken)
            .doOnNext(productNode -> {
                String sku = productNode.path("retailer_id").asText();
                if (sku != null && !sku.isBlank()) {
                    metaSkus.add(sku);
                    processSingleProductSync(catalog, productNode);
                }
            })
            .doOnComplete(() -> {
                // Lógica de "Limpeza" (Delete Orphans)
                // Se estava no local (localSkus), mas não veio da Meta (metaSkus) -> DELETAR
                localSkus.removeAll(metaSkus);

                if (!localSkus.isEmpty()) {
                    log.info("Detectados {} produtos deletados na Meta. Removendo localmente...", localSkus.size());
                    
                    // Deleta em lotes para não estourar o limite de parâmetros do SQL (IN clause)
                    List<List<String>> deleteBatches = Lists.partition(new ArrayList<>(localSkus), 500);
                    for (List<String> batch : deleteBatches) {
                        productRepository.deleteAllByCatalogIdAndSkuIn(catalogId, batch);
                    }
                } else {
                    log.info("Sincronização concluída. Nenhum produto para deletar.");
                }
                
                catalog.setUpdatedAt(LocalDateTime.now());
                catalogRepository.save(catalog);
            })
            .doOnError(e -> log.error("Erro fatal durante sincronização do catálogo " + catalogId, e))
            .subscribe();
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
                .doOnSuccess(ps -> log.info("Product Set '{}' criado com sucesso. Meta ID: {}", ps.getName(), ps.getMetaProductSetId()))
                .doOnError(e -> log.error("Erro ao criar Product Set na Meta: {}", e.getMessage()));
    }

    /**
     * Vincula o Catálogo à WABA (WhatsApp Business Account).
     * POST /{WABA_ID}/product_catalogs
     */
    private Mono<Void> connectCatalogToWaba(Company company, String metaCatalogId) {
        
        List<WhatsAppPhoneNumber> numbers = phoneNumberRepository.findByCompany(company);
    
        // Extrai WABA IDs únicos (para não chamar a API 2x para a mesma WABA)
        Set<String> uniqueWabaIds = numbers.stream()
                .map(WhatsAppPhoneNumber::getWabaId)
                .collect(Collectors.toSet());

        if (uniqueWabaIds.isEmpty()) {
            log.warn("Nenhuma WABA encontrada para vincular o catálogo.");
            return Mono.empty();
        }

        // Dispara vínculo para todas as WABAs encontradas
        List<Mono<Void>> tasks = uniqueWabaIds.stream().map(wabaId -> {
            String endpoint = graphApiBaseUrl + "/" + wabaId + "/product_catalogs";
            Map<String, String> body = new HashMap<>();
            body.put("catalog_id", metaCatalogId);
            
            return webClientBuilder.build().post().uri(endpoint)
                // ... headers/body ...
                .retrieve().bodyToMono(JsonNode.class).then();
        }).toList();

        return Mono.when(tasks);
    }

    /**
     * Vincula o Catálogo ao Número de Telefone (para exibir na loja/perfil).
     * POST /{PHONE_NUMBER_ID}/whatsapp_business_catalogs
     */
    private Mono<Void> connectCatalogToPhoneNumber(Company company, String metaCatalogId) {
        
        // 1. Buscar todos os números da empresa
        List<WhatsAppPhoneNumber> phoneNumbers = phoneNumberRepository.findByCompany(company);

        if (phoneNumbers.isEmpty()) {
            log.warn("Nenhum número de telefone encontrado para a empresa {}. O catálogo {} não será vinculado a nenhum perfil.", 
                    company.getId(), metaCatalogId);
            return Mono.empty();
        }

        // 2. Criar uma lista de tarefas (Monos) para vincular cada número
        List<Mono<Void>> tasks = phoneNumbers.stream().map(phone -> {
            
            String endpoint = graphApiBaseUrl + "/" + phone.getPhoneNumberId() + "/whatsapp_business_catalogs";
            
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
                    .doOnSuccess(json -> log.info("Catálogo {} vinculado com sucesso ao número {} ({})", 
                            metaCatalogId, phone.getPhoneNumberId(), phone.getAlias()))
                    .doOnError(e -> log.warn("Falha ao vincular catálogo {} ao número {}: {}", 
                            metaCatalogId, phone.getPhoneNumberId(), e.getMessage()))
                    .then(); // Retorna Mono<Void> e continua mesmo se der erro (log warning)
        }).toList();

        // 3. Executa todas as vinculações em paralelo e aguarda o término
        return Mono.when(tasks);
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
                log.warn("Erro ao parsear preço do produto {}: {}", metaProd.getRetailerId(), e.getMessage());
            }

            productRepository.save(product);
        }
        log.info("Lote de produtos sincronizado para o catálogo {}", catalog.getId());
    }

    private void sendBatchRequest(String metaCatalogId, List<BatchItem> requests, String accessToken) {
        String url = graphApiBaseUrl + "/" + metaCatalogId + "/batch";
        
        MetaProductBatchRequest payload = new MetaProductBatchRequest();
        payload.setRequests(requests);

        try {
            webClientBuilder.build().post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                        response -> log.debug("Batch enviado com sucesso para Meta: {} itens", requests.size()),
                        error -> log.error("Erro ao enviar batch para Meta: ", error)
                    );
        } catch (Exception e) {
            log.error("Erro ao montar requisição batch", e);
        }
    }

    private Flux<JsonNode> fetchProductsRecursive(String metaCatalogId, String accessToken) {
        String initialUrl = graphApiBaseUrl + "/" + metaCatalogId + "/products?fields=retailer_id,name,description,price,currency,image_url,availability&limit=1000";

        return fetchPage(initialUrl, accessToken)
                .expand(response -> {
                    if (response.has("paging") && response.path("paging").has("next")) {
                        String nextUrl = response.path("paging").path("next").asText();
                        return fetchPage(nextUrl, accessToken);
                    }
                    return Mono.empty();
                })
                .flatMap(response -> {
                    if (response.has("data")) {
                        return Flux.fromIterable(response.get("data"));
                    }
                    return Flux.empty();
                });
    }

    private Mono<JsonNode> fetchPage(String url, String accessToken) {
        return webClientBuilder.build().get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private void processSingleProductSync(Catalog catalog, JsonNode node) {
        try {
            String sku = node.path("retailer_id").asText();
            Product product = productRepository.findByCatalogAndSku(catalog, sku)
                    .orElse(new Product());

            product.setCatalog(catalog);
            product.setSku(sku);
            product.setName(node.path("name").asText());
            product.setDescription(node.path("description").asText());
            product.setImageUrl(node.path("image_url").asText());
            
            // Tratamento simples de preço (pode precisar de melhor parsing dependendo do formato "19.99")
            String priceStr = node.path("price").asText().replaceAll("[^0-9.]", "");
            if (!priceStr.isBlank()) {
                product.setPrice(new java.math.BigDecimal(priceStr));
            }
            product.setCurrency(node.path("currency").asText("BRL"));
            
            String availability = node.path("availability").asText();
            product.setInStock("in stock".equalsIgnoreCase(availability));

            productRepository.save(product);
        } catch (Exception e) {
            log.error("Erro ao processar produto individual no sync: {}", node, e);
        }
    }

    private BatchItem convertToBatchItem(Product product) {
        // Implementação simplificada do conversor para o formato da Meta
        return BatchItem.builder()
                .method("UPDATE") // UPDATE funciona como Upsert na Meta
                .retailerId(product.getSku())
                .data(ProductAttributes.builder()
                        .name(product.getName())
                        .description(product.getDescription())
                        .availability(product.isInStock() ? "in stock" : "out of stock")
                        .currency(product.getCurrency())
                        .imageUrl(product.getImageUrl())
                        .build())
                .build();
    }
}