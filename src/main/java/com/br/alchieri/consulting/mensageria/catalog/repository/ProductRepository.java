package com.br.alchieri.consulting.mensageria.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.Product;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Optional<Product> findByCatalogAndSku(Catalog catalog, String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.catalog.company.id = :companyId AND p.sku = :sku")
    Optional<Product> findByCompanyIdAndSkuWithLock(@Param("companyId") Long companyId, @Param("sku") String sku);

    Optional<Product> findByCompanyIdAndSku(Long companyId, String sku);

    Optional<Product> findBySku(String retailerId);
}
