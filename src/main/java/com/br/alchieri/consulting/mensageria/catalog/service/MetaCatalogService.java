package com.br.alchieri.consulting.mensageria.catalog.service;

import java.util.List;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;
import com.br.alchieri.consulting.mensageria.catalog.model.ProductSet;
import com.br.alchieri.consulting.mensageria.model.Company;

import reactor.core.publisher.Mono;

public interface MetaCatalogService {

    Mono<Catalog> createCatalog(String catalogName, String vertical, Long metaBusinessManagerId, Company company);
    
    void upsertProducts(Long catalogId, List<Product> products);
    
    void deleteProducts(Long catalogId, List<String> skus);
    
    Mono<ProductSet> createProductSet(Long catalogId, String name, List<String> retailerIds, Company company);

    void syncCatalogsFromMeta(Company company);

    void syncProductsFromMeta(Long catalogId);
}
