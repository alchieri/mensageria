package com.br.alchieri.consulting.mensageria.chat.service.impl.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Busca paginada para uma empresa específica
    Page<Invoice> findByCompanyOrderByIssueDateDesc(Company company, Pageable pageable);

    // Busca uma fatura específica pelo ID e pela empresa dona (para segurança do cliente)
    Optional<Invoice> findByIdAndCompany(Long id, Company company);
    
    // Busca paginada de TODAS as faturas (para admin)
    @Query(value = "SELECT i FROM Invoice i JOIN FETCH i.company",
           countQuery = "SELECT COUNT(i) FROM Invoice i")
    Page<Invoice> findAllWithCompany(Pageable pageable);
}
