package com.br.alchieri.consulting.mensageria.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.catalog.model.Catalog;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface CatalogRepository extends JpaRepository<Catalog, Long> {
    Optional<Catalog> findByCompanyAndIsDefaultTrue(Company company);
    Optional<Catalog> findByMetaCatalogId(String metaCatalogId);
}
