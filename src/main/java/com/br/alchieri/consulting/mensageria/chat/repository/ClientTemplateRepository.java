package com.br.alchieri.consulting.mensageria.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.br.alchieri.consulting.mensageria.chat.model.ClientTemplate;
import com.br.alchieri.consulting.mensageria.model.Company;

@Repository
public interface ClientTemplateRepository extends JpaRepository<ClientTemplate, Long> {

    Optional<ClientTemplate> findByCompanyAndTemplateNameAndLanguage(Company company, String templateName, String language);

    List<ClientTemplate> findAllByTemplateNameAndLanguage(String templateName, String language);

    Optional<ClientTemplate> findByTemplateNameAndLanguage(String templateName, String language); // Para webhook se cliente não for identificado de imediato

    List<ClientTemplate> findAllByCompany(Company company);

    Optional<ClientTemplate> findByMetaTemplateId(String metaTemplateIdFromWebhook);

    // Busca paginada para um cliente específico, com JOIN FETCH na empresa
    @Query(value = "SELECT ct FROM ClientTemplate ct JOIN FETCH ct.company WHERE ct.company = :company",
           countQuery = "SELECT COUNT(ct) FROM ClientTemplate ct WHERE ct.company = :company")
    Page<ClientTemplate> findByCompany(Company company, Pageable pageable);

    // Busca paginada de TODOS os templates, com JOIN FETCH na empresa (para Admin)
    @Query(value = "SELECT ct FROM ClientTemplate ct JOIN FETCH ct.company",
           countQuery = "SELECT COUNT(ct) FROM ClientTemplate ct")
    Page<ClientTemplate> findAllWithCompany(Pageable pageable);

    // Busca todos os templates (diferentes idiomas) com um nome específico para uma empresa
    List<ClientTemplate> findByCompanyAndTemplateName(Company company, String templateName);

    /**
     * Conta quantos templates uma empresa possui que estão em um dos status fornecidos.
     * Útil para contar templates "ativos".
     * @param company A empresa.
     * @param statuses A coleção de status a serem contados.
     * @return O número de templates encontrados.
     */
    long countByCompanyAndStatusIn(Company company, Collection<String> statuses);

    // Busca por ID, Empresa e Status APROVADO
    Optional<ClientTemplate> findByIdAndCompanyAndStatus(Long id, Company company, String status);
}
