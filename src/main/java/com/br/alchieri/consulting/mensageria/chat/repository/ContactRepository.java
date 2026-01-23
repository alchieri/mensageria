package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.Contact;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Busca contatos paginados por empresa
    Page<Contact> findByCompany(Company company, Pageable pageable);

    // Busca um contato específico pelo ID e pela empresa dona (para segurança)
    Optional<Contact> findByIdAndCompany(Long id, Company company);
    
    // Busca um contato pelo número de telefone dentro de uma empresa
    Optional<Contact> findByCompanyAndPhoneNumber(Company company, String phoneNumber);

    List<Contact> findByCompanyAndPhoneNumberIn(Company company, List<String> phoneNumbers);

    @Query("SELECT c FROM Contact c JOIN c.tags t WHERE c.company = :company AND t.name IN :tagNames")
    List<Contact> findByCompanyAndTagsNameIn(Company company, List<String> tagNames);
}
