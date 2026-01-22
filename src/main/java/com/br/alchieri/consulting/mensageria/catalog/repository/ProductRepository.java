package com.br.alchieri.consulting.mensageria.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCatalogAndSku(Catalog catalog, String sku);
}
