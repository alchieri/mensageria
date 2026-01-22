package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.br.alchieri.consulting.mensageria.chat.model.Flow;
import com.br.alchieri.consulting.mensageria.chat.model.enums.FlowStatus;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    Page<Flow> findByCompany(Company company, Pageable pageable);
    Optional<Flow> findByIdAndCompany(Long id, Company company);

    @Query("SELECT f FROM Flow f LEFT JOIN FETCH f.company WHERE f.metaFlowId = :metaFlowId")
    Optional<Flow> findByMetaFlowId(@Param("metaFlowId") String metaFlowId);

    Optional<Flow> findByCompanyAndName(Company company, String flowName);
    
    /**
     * Conta quantos flows uma empresa possui com um status específico.
     * Útil para contar flows "ativos" (PUBLISHED).
     * @param company A empresa.
     * @param status O status a ser contado.
     * @return O número de flows encontrados.
     */
    long countByCompanyAndStatus(Company company, FlowStatus status);
}
