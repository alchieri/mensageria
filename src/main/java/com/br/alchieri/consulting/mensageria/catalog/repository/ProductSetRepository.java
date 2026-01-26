package com.br.alchieri.consulting.mensageria.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.catalog.model.ProductSet;

@Repository
public interface ProductSetRepository extends JpaRepository<ProductSet, Long> {
    
    List<ProductSet> findByCatalog(Catalog catalog);
}
