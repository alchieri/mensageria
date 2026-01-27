package com.br.alchieri.consulting.mensageria.catalog.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findByCatalogAndSku(Catalog catalog, String sku);

    // 1. Projeção: Busca apenas os SKUs para não carregar objetos pesados em memória
    @Query("SELECT p.sku FROM Product p WHERE p.catalog.id = :catalogId")
    Set<String> findAllSkusByCatalogId(@Param("catalogId") Long catalogId);

    // 2. Delete em Batch: Remove produtos que não existem mais na Meta
    @Modifying
    @Query("DELETE FROM Product p WHERE p.catalog.id = :catalogId AND p.sku IN :skus")
    void deleteAllByCatalogIdAndSkuIn(@Param("catalogId") Long catalogId, @Param("skus") Collection<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.catalog.company.id = :companyId AND p.sku = :sku")
    Optional<Product> findByCompanyIdAndSkuWithLock(@Param("companyId") Long companyId, @Param("sku") String sku);

    @Query("SELECT p FROM Product p WHERE p.catalog.company.id = :companyId AND p.sku = :sku")
    Optional<Product> findByCompanyIdAndSku(@Param("companyId") Long companyId, @Param("sku") String sku);

    Optional<Product> findBySku(String retailerId);
}
