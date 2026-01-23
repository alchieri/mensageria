package com.br.alchieri.consulting.mensageria.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByName(String name);
    Optional<Company> findByMetaWabaId(String metaWabaId);
    Optional<Company> findByMetaPrimaryPhoneNumberId(String metaPhoneNumberId);
    Optional<Company> findByDocumentNumber(String documentNumber);

    @Query("SELECT comp FROM Company comp " +
           "LEFT JOIN FETCH comp.users " +
           "LEFT JOIN FETCH comp.billingPlan " +
           "WHERE comp.id = :id")
    Optional<Company> findByIdWithDetails(@Param("id") Long id);
    // Adicionar outros métodos de busca conforme necessário
}
