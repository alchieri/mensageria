package com.br.alchieri.consulting.mensageria.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.WhatsAppPhoneNumber;

@Repository
public interface WhatsAppPhoneNumberRepository extends JpaRepository<WhatsAppPhoneNumber, Long> {
    
    // Busca por ID da Meta (usado no Webhook)
    Optional<WhatsAppPhoneNumber> findByPhoneNumberId(String phoneNumberId);
    
    // Lista todos da empresa
    List<WhatsAppPhoneNumber> findByCompany(Company company);
    
    // Busca o padrão da empresa
    Optional<WhatsAppPhoneNumber> findFirstByCompanyAndIsDefaultTrue(Company company);
    
    // Busca um específico da empresa (segurança no envio)
    Optional<WhatsAppPhoneNumber> findByCompanyAndPhoneNumberId(Company company, String phoneNumberId);

}
