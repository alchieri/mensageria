package com.br.alchieri.consulting.mensageria.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.BillingPlan;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {

    Optional<BillingPlan> findByCompany(Company company);
    Optional<BillingPlan> findByCompanyId(Long companyId);
}
