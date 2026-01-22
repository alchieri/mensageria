package com.br.alchieri.consulting.mensageria.service.impl;

import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.alchieri.consulting.mensageria.chat.model.enums.OnboardingStatus;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.CompanyRepository;
import com.br.alchieri.consulting.mensageria.chat.service.impl.repository.UserRepository;
import com.br.alchieri.consulting.mensageria.dto.request.PublicRegistrationRequest;
import com.br.alchieri.consulting.mensageria.dto.response.RegistrationResponse;
import com.br.alchieri.consulting.mensageria.exception.BusinessException;
import com.br.alchieri.consulting.mensageria.model.Company;
import com.br.alchieri.consulting.mensageria.model.User;
import com.br.alchieri.consulting.mensageria.model.enums.Role;
import com.br.alchieri.consulting.mensageria.service.RegistrationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public RegistrationResponse registerNewCompanyAndUser(PublicRegistrationRequest request) {
        // Validar se a empresa já existe
        if (companyRepository.findByName(request.getCompany().getName()).isPresent()) {
            throw new BusinessException("Empresa com o nome '" + request.getCompany().getName() + "' já existe.");
        }
        // Validar se o email do usuário já existe
        if (userRepository.findByEmail(request.getUser().getEmail()).isPresent()) {
            throw new BusinessException("Email '" + request.getUser().getEmail() + "' já está em uso.");
        }

        // 1. Criar a empresa
        Company newCompany = new Company();
        newCompany.setName(request.getCompany().getName());
        newCompany.setContactEmail(request.getCompany().getContactEmail());
        newCompany.setEnabled(true);
        newCompany.setOnboardingStatus(OnboardingStatus.NOT_STARTED);
        // Salvar a empresa primeiro para obter o ID
        Company savedCompany = companyRepository.save(newCompany);

        // 2. Criar o usuário administrador da empresa
        User companyAdminUser = new User();
        // O username para login pode ser o email
        companyAdminUser.setUsername(request.getUser().getEmail());
        companyAdminUser.setEmail(request.getUser().getEmail());
        companyAdminUser.setFullName(request.getUser().getFullName());
        companyAdminUser.setPassword(passwordEncoder.encode(request.getUser().getPassword()));
        companyAdminUser.setEnabled(true);
        // Definir roles: ele é um administrador da empresa e um usuário
        companyAdminUser.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_COMPANY_ADMIN));
        // Associar o usuário à empresa recém-criada
        companyAdminUser.setCompany(savedCompany);

        User savedUser = userRepository.save(companyAdminUser);

        return RegistrationResponse.from(savedCompany, savedUser);
    }
}
