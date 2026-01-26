package com.br.alchieri.consulting.mensageria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.MetaBusinessManager;

@Repository
public interface MetaBusinessManagerRepository extends JpaRepository<MetaBusinessManager, Long> {
    List<MetaBusinessManager> findByCompany(Company company);
    Optional<MetaBusinessManager> findByCompanyAndMetaBusinessId(Company company, String metaBusinessId);
}
