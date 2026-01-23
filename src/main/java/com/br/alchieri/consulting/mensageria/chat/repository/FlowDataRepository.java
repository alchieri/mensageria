package com.br.alchieri.consulting.mensageria.chat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.FlowData;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface FlowDataRepository extends JpaRepository<FlowData, Long> {

    Page<FlowData> findByCompany(Company company, Pageable pageable);

    @Query("SELECT fd FROM FlowData fd WHERE fd.flow.id = :flowId AND fd.company = :company ORDER BY fd.receivedAt DESC")
    Page<FlowData> findByFlowIdAndCompany(@Param("flowId") Long flowId, @Param("company") Company company, Pageable pageable);
}
