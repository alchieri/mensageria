package com.br.alchieri.consulting.mensageria.catalog.service;

import java.util.List;

import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.ProductSet;
import com.br.alchieri.consulting.mensageria.model.Company;

import reactor.core.publisher.Mono;

public interface MetaCatalogService {

    Mono<Catalog> createCatalog(String catalogName, String vertical, Long metaBusinessManagerId, Company company);
    Mono<Void> upsertProducts(List<ProductSyncRequest> products, Company company);
    Mono<Void> deleteProducts(List<String> skus, Company company);
    Mono<ProductSet> createProductSet(Long catalogId, String name, List<String> retailerIds, Company company);

    // NOVOS MÉTODOS DE SINCRONIZAÇÃO (Inbound)
    void syncCatalogsFromMeta(Company company);
    void syncProductsFromMeta(Long localCatalogId, Company company);
}
