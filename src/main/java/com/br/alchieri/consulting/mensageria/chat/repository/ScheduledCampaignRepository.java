package com.br.alchieri.consulting.mensageria.chat.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.br.alchieri.consulting.mensageria.chat.model.ScheduledCampaign;
import com.br.alchieri.consulting.mensageria.model.Company;

public interface ScheduledCampaignRepository extends JpaRepository<ScheduledCampaign, Long> {

    Page<ScheduledCampaign> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);
    // Adicionar métodos de busca customizados se necessário no futuro
    // Ex: Page<ScheduledCampaign> findByCompany(Company company, Pageable pageable);
}