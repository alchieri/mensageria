package com.br.alchieri.consulting.mensageria.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.dto.request.CreateCompanyRequest;
import com.br.alchieri.consulting.mensageria.dto.request.UpdateCallbacksRequest;
import com.br.alchieri.consulting.mensageria.dto.request.UpdateCompanyRequest;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;

public interface CompanyService {

    Company createCompany(CreateCompanyRequest request, User creator); // Admin do BSP cria empresa
    Optional<Company> findById(Long companyId);
    Page<Company> findAllCompanies(Pageable pageable); // Admin do BSP lista todas
    Company updateCompany(Long companyId, UpdateCompanyRequest request, User updater); // Admin do BSP atualiza
    Company getCompanyOfUser(User user); // Para usuários obterem sua própria empresa
    // Métodos para gerenciar usuários dentro de uma empresa (Company Admin)
    User addUserToCompany(Long companyId, String usernameOfUserToAdd, Role roleForUserInCompany, User administrator);
    void removeUserFromCompany(Long companyId, Long userIdToRemove, User administrator);
    /**
     * Atualiza as URLs de callback para uma empresa específica.
     * @param company A empresa a ser atualizada.
     * @param request O DTO contendo as novas URLs.
     * @return A entidade Company atualizada.
     */
    Company updateCompanyCallbacks(Company company, UpdateCallbacksRequest request);
}
