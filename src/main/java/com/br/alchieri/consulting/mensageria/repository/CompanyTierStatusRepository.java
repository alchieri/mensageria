package com.br.alchieri.consulting.mensageria.repository;

import java.time.YearMonth;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.br.alchieri.consulting.mensageria.chat.model.enums.TemplateCategory;
import com.br.alchieri.consulting.mensageria.model.CompanyTierStatus;

public interface CompanyTierStatusRepository extends JpaRepository<CompanyTierStatus, Long> {
    
    Optional<CompanyTierStatus> findByWabaIdAndCategoryAndEffectiveMonth(
            String wabaId, TemplateCategory category, YearMonth effectiveMonth);
}
