package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.Bot;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface BotRepository extends JpaRepository<Bot, Long> {
    
    List<Bot> findByCompany(Company company);
    List<Bot> findByCompanyAndIsActiveTrue(Company company);
}
