package com.br.alchieri.consulting.mensageria.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyCreateUserRequest;
import com.br.alchieri.consulting.mensageria.chat.dto.request.CompanyUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.AdminCreateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.AdminUpdateUserRequest;
import com.br.alchieri.consulting.mensageria.dto.request.RegisterUserRequest;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;

public interface UserService {

    User registerNewUser(RegisterUserRequest request, Long companyId, Set<Role> defaultRoles);
    Optional<User> findById(Long userId);
    Optional<User> findByUsername(String username);
    Page<User> findAllUsers(Pageable pageable); // Admin: listar todos os usuários
    Page<User> findUsersByCompany(Long companyId, Pageable pageable); // Admin ou Company Admin: listar usuários de uma empresa
    User updateUserByAdmin(Long userId, AdminUpdateUserRequest request);
    /**
     * Cria um novo usuário. Operação executada por um administrador BSP.
     * @param request DTO com os dados do novo usuário.
     * @return A entidade User criada.
     */
    User createUserByAdmin(AdminCreateUserRequest request);

    // --- MÉTODOS PARA COMPANY ADMIN ---

    /** Cria um novo usuário para uma empresa específica, acionado por um admin da empresa. */
    User createUserForCompany(CompanyCreateUserRequest request, Company company);

    /** Atualiza um usuário de uma empresa específica, acionado por um admin da empresa. */
    User updateUserInCompany(Long userId, CompanyUpdateUserRequest request, Company company);

    /** Deleta um usuário de uma empresa. */
    void deleteUserInCompany(Long userId, Company company);
}
