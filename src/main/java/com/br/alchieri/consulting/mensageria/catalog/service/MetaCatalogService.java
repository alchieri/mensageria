package com.br.alchieri.consulting.mensageria.catalog.service;

import java.util.List;

import com.br.alchieri.consulting.mensageria.catalog.dto.request.ProductSyncRequest;
import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.model.Company;

import reactor.core.publisher.Mono;

public interface MetaCatalogService {

    Mono<Catalog> createCatalog(String catalogName, Company company); // Novo método
    Mono<Void> upsertProducts(List<ProductSyncRequest> products, Company company);
    Mono<Void> deleteProducts(List<String> skus, Company company);
}
